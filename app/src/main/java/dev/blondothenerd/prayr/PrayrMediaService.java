package dev.blondothenerd.prayr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genuine Android Auto media companion. Active prayer items are exposed as
 * short spoken tracks; completing a track ticks that item for this round.
 */
public final class PrayrMediaService extends MediaBrowserService implements TextToSpeech.OnInitListener {
    private static final String ROOT_ID = "prayr_root";
    private static final String MEDIA_PREFIX = "prayer:";
    private static final String CHANNEL_ID = "prayr_car_playback";
    private static final int FOREGROUND_ID = 8701;
    private static final String CUSTOM_PRAYED = "dev.blondothenerd.prayr.media.PRAYED";
    private static final String CUSTOM_MUTE = "dev.blondothenerd.prayr.media.MUTE";

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private TextToSpeech speech;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean speechReady;
    private String pendingPrayerId;
    private String currentPrayerId;
    private String currentUtteranceId;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setOnAudioFocusChangeListener(this::onAudioFocusChanged, main)
            .build();

        session = new MediaSession(this, "prayr_android_auto");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { playDefault(); }
            @Override public void onPause() { pauseSpeaking(); }
            @Override public void onStop() { stopSpeaking(true); }
            @Override public void onSkipToNext() { skip(true); }
            @Override public void onSkipToPrevious() { skip(false); }
            @Override public void onPlayFromMediaId(String mediaId, Bundle extras) { playFromMediaId(mediaId); }
            @Override public void onPlayFromSearch(String query, Bundle extras) { playFromSearch(query); }
            @Override public void onSkipToQueueItem(long id) { playQueueItem(id); }
            @Override public void onCustomAction(String action, Bundle extras) {
                if (CUSTOM_PRAYED.equals(action)) markCurrentPrayed();
                if (CUSTOM_MUTE.equals(action)) muteReminders();
            }
        });
        setSessionToken(session.getSessionToken());
        session.setActive(true);
        refreshQueue();
        publishState(PlaybackState.STATE_STOPPED, null);

        speech = new TextToSpeech(this, this);
        speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                main.post(() -> {
                    if (utteranceId.equals(currentUtteranceId)) publishState(PlaybackState.STATE_PLAYING, null);
                });
            }

            @Override public void onDone(String utteranceId) {
                main.post(() -> finishPrayer(utteranceId));
            }

            @Override public void onError(String utteranceId) {
                main.post(() -> failSpeech(utteranceId));
            }

            @Override public void onError(String utteranceId, int errorCode) {
                main.post(() -> failSpeech(utteranceId));
            }
        });
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // Android Auto can start this service before the phone Activity has ever opened.
        if (!isTrustedMediaClient(clientPackageName, clientUid)) return null;
        refreshQueue();
        return new BrowserRoot(ROOT_ID, null);
    }

    /** Keep prayer names and reasons away from arbitrary third-party media clients. */
    private boolean isTrustedMediaClient(String packageName, int uid) {
        if (packageName == null) return false;
        String[] uidPackages = getPackageManager().getPackagesForUid(uid);
        boolean uidMatches = false;
        if (uidPackages != null) {
            for (String candidate : uidPackages) if (packageName.equals(candidate)) uidMatches = true;
        }
        if (!uidMatches) return false;
        if (getPackageName().equals(packageName)
            || "com.google.android.projection.gearhead".equals(packageName)
            || "com.google.android.gms".equals(packageName)) return true;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        if (ROOT_ID.equals(parentId)) {
            for (Prayer prayer : activePrayers()) {
                items.add(new MediaBrowser.MediaItem(descriptionFor(prayer), MediaBrowser.MediaItem.FLAG_PLAYABLE));
            }
        }
        result.sendResult(items);
    }

    @Override
    public void onInit(int status) {
        speechReady = status == TextToSpeech.SUCCESS;
        if (!speechReady) {
            publishState(PlaybackState.STATE_ERROR, "Text-to-speech is unavailable on this phone");
            return;
        }
        int language = speech.setLanguage(Locale.getDefault());
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            speech.setLanguage(Locale.UK);
        }
        speech.setSpeechRate(0.92f);
        speech.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build());
        if (pendingPrayerId != null) {
            String id = pendingPrayerId;
            pendingPrayerId = null;
            Prayer prayer = PrayerStore.find(this, id);
            if (prayer != null && !prayer.healed) speak(prayer);
        }
    }

    private List<Prayer> activePrayers() {
        List<Prayer> active = new ArrayList<>();
        for (Prayer prayer : PrayerStore.getAll(this)) if (!prayer.healed) active.add(prayer);
        return active;
    }

    private MediaDescription descriptionFor(Prayer prayer) {
        String type = prayer.isPraise() ? "Praise: " : "Pray for: ";
        String title = (prayer.prayed ? "✓ " : "") + type + prayer.name;
        String reason = cleanReason(prayer);
        Bundle extras = new Bundle();
        extras.putString("prayr_state", prayer.prayed ? "prayed" : "waiting");
        extras.putString("prayr_type", prayer.type);
        return new MediaDescription.Builder()
            .setMediaId(MEDIA_PREFIX + prayer.id)
            .setTitle(title)
            .setSubtitle(reason)
            .setDescription(prayer.prayed ? "Prayed this round" : "Ready to pray")
            .setIconUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.ic_launcher))
            .setExtras(extras)
            .build();
    }

    private void refreshQueue() {
        if (session == null) return;
        List<MediaSession.QueueItem> queue = new ArrayList<>();
        List<Prayer> prayers = activePrayers();
        for (int i = 0; i < prayers.size(); i++) {
            queue.add(new MediaSession.QueueItem(descriptionFor(prayers.get(i)), i + 1L));
        }
        session.setQueue(queue);
        session.setQueueTitle("Prayer list");
    }

    private void playDefault() {
        if (currentPrayerId != null) {
            Prayer current = PrayerStore.find(this, currentPrayerId);
            if (current != null && !current.healed) {
                playPrayer(current);
                return;
            }
        }
        List<Prayer> prayers = activePrayers();
        for (Prayer prayer : prayers) {
            if (!prayer.prayed) {
                playPrayer(prayer);
                return;
            }
        }
        if (!prayers.isEmpty()) playPrayer(prayers.get(0));
        else publishState(PlaybackState.STATE_ERROR, "No active prayers yet — add one on your phone");
    }

    private void playFromMediaId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith(MEDIA_PREFIX)) {
            publishState(PlaybackState.STATE_ERROR, "That prayer is unavailable");
            return;
        }
        Prayer prayer = PrayerStore.find(this, mediaId.substring(MEDIA_PREFIX.length()));
        if (prayer == null || prayer.healed) {
            publishState(PlaybackState.STATE_ERROR, "That prayer is no longer active");
            notifyChildrenChanged(ROOT_ID);
            return;
        }
        playPrayer(prayer);
    }

    private void playFromSearch(String query) {
        String wanted = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Prayer prayer : activePrayers()) {
            if (wanted.isEmpty()
                || prayer.name.toLowerCase(Locale.ROOT).contains(wanted)
                || prayer.reason.toLowerCase(Locale.ROOT).contains(wanted)) {
                playPrayer(prayer);
                return;
            }
        }
        publishState(PlaybackState.STATE_ERROR, "No matching prayer was found");
    }

    private void playQueueItem(long queueId) {
        List<Prayer> prayers = activePrayers();
        int index = (int) queueId - 1;
        if (index >= 0 && index < prayers.size()) playPrayer(prayers.get(index));
    }

    private void skip(boolean forward) {
        List<Prayer> prayers = activePrayers();
        if (prayers.isEmpty()) {
            publishState(PlaybackState.STATE_ERROR, "No active prayers yet");
            return;
        }
        int current = -1;
        for (int i = 0; i < prayers.size(); i++) {
            if (prayers.get(i).id.equals(currentPrayerId)) {
                current = i;
                break;
            }
        }
        int target;
        if (forward) target = current < 0 ? 0 : (current + 1) % prayers.size();
        else target = current <= 0 ? prayers.size() - 1 : current - 1;
        playPrayer(prayers.get(target));
    }

    private void playPrayer(Prayer prayer) {
        stopSpeechOnly();
        currentPrayerId = prayer.id;
        setMetadata(prayer);
        if (!speechReady) {
            pendingPrayerId = prayer.id;
            publishState(PlaybackState.STATE_CONNECTING, null);
            return;
        }
        speak(prayer);
    }

    private void speak(Prayer prayer) {
        if (!requestAudioFocus()) {
            publishState(PlaybackState.STATE_ERROR, "The car audio system is currently unavailable");
            return;
        }
        currentUtteranceId = "prayr-" + prayer.id + "-" + System.currentTimeMillis();
        publishState(PlaybackState.STATE_PLAYING, null);
        showPlaybackNotification(prayer);
        int result = speech.speak(spokenText(prayer), TextToSpeech.QUEUE_FLUSH, null, currentUtteranceId);
        if (result == TextToSpeech.ERROR) failSpeech(currentUtteranceId);
    }

    private String spokenText(Prayer prayer) {
        StringBuilder value = new StringBuilder(prayer.isPraise() ? "Praise for " : "Pray for ");
        value.append(prayer.name == null || prayer.name.trim().isEmpty() ? "this person" : prayer.name.trim());
        String reason = prayer.reason == null ? "" : prayer.reason.trim();
        if (!reason.isEmpty()) value.append(". ").append(reason);
        else value.append(". Take a quiet moment for them.");
        return value.toString();
    }

    private String cleanReason(Prayer prayer) {
        if (prayer.reason == null || prayer.reason.trim().isEmpty()) return "Take a quiet moment for them";
        return prayer.reason.trim();
    }

    private void setMetadata(Prayer prayer) {
        String title = (prayer.isPraise() ? "Praise for " : "Pray for ") + prayer.name;
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, MEDIA_PREFIX + prayer.id)
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, cleanReason(prayer))
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "prayr")
            .build());
    }

    private void finishPrayer(String utteranceId) {
        if (currentUtteranceId == null || !currentUtteranceId.equals(utteranceId)) return;
        currentUtteranceId = null;
        if (currentPrayerId != null) {
            PrayerStore.markPrayed(this, currentPrayerId, true);
            cancelReminder(currentPrayerId);
        }
        abandonAudioFocus();
        stopForeground(true);
        refreshQueue();
        notifyChildrenChanged(ROOT_ID);
        publishState(PlaybackState.STATE_STOPPED, null);
    }

    private void failSpeech(String utteranceId) {
        if (currentUtteranceId != null && !currentUtteranceId.equals(utteranceId)) return;
        currentUtteranceId = null;
        abandonAudioFocus();
        stopForeground(true);
        publishState(PlaybackState.STATE_ERROR, "The prayer could not be read aloud");
    }

    private void pauseSpeaking() {
        stopSpeechOnly();
        abandonAudioFocus();
        stopForeground(true);
        publishState(PlaybackState.STATE_PAUSED, null);
    }

    private void stopSpeaking(boolean clearPending) {
        if (clearPending) pendingPrayerId = null;
        stopSpeechOnly();
        abandonAudioFocus();
        stopForeground(true);
        publishState(PlaybackState.STATE_STOPPED, null);
    }

    private void stopSpeechOnly() {
        currentUtteranceId = null;
        if (speech != null) speech.stop();
    }

    private void markCurrentPrayed() {
        if (currentPrayerId == null) return;
        stopSpeechOnly();
        PrayerStore.markPrayed(this, currentPrayerId, true);
        cancelReminder(currentPrayerId);
        abandonAudioFocus();
        stopForeground(true);
        refreshQueue();
        notifyChildrenChanged(ROOT_ID);
        publishState(PlaybackState.STATE_STOPPED, null);
    }

    private void muteReminders() {
        AppSettings.setDrivingMuted(this, true);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancelAll();
        stopSpeaking(true);
    }

    private boolean requestAudioFocus() {
        return audioManager != null && audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void onAudioFocusChanged(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pauseSpeaking();
    }

    private void publishState(int state, String error) {
        long actions = PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_STOP
            | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackState.ACTION_PLAY_FROM_SEARCH
            | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS
            | PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;
        PlaybackState.Builder builder = new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state == PlaybackState.STATE_PLAYING ? 1f : 0f)
            .addCustomAction(new PlaybackState.CustomAction.Builder(CUSTOM_PRAYED, getString(R.string.car_action_prayed), R.drawable.ic_done).build())
            .addCustomAction(new PlaybackState.CustomAction.Builder(CUSTOM_MUTE, getString(R.string.car_action_mute), R.drawable.ic_mute).build());
        if (error != null) builder.setErrorMessage(error);
        session.setPlaybackState(builder.build());
    }

    private void showPlaybackNotification(Prayer prayer) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.car_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.car_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, FOREGROUND_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = (prayer.isPraise() ? "Praise for " : "Pray for ") + prayer.name;
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(AppSettings.primaryColor(this))
            .setContentTitle(title)
            .setContentText(cleanReason(prayer))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()))
            .build();
        startForeground(FOREGROUND_ID, notification);
    }

    private void cancelReminder(String prayerId) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(6000 + Math.abs(prayerId.hashCode() % 100000));
    }

    @Override
    public void onDestroy() {
        pendingPrayerId = null;
        stopSpeechOnly();
        abandonAudioFocus();
        stopForeground(true);
        if (speech != null) speech.shutdown();
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        super.onDestroy();
    }
}
