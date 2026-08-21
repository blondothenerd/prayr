<div align="center">

<h1>prayr 🙏</h1>

<p><strong>A simple Android prayer companion by blondothenerd.</strong></p>

<p>
  Keep prayer and praise items in rotation, receive configurable local reminders,
  and mark items as prayed without turning prayer into another productivity dashboard.
</p>

<p>
  <a href="https://github.com/blondothenerd/prayr/actions/workflows/android.yml">
    <img src="https://github.com/blondothenerd/prayr/actions/workflows/android.yml/badge.svg" alt="Android CI">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
  </a>
</p>

<br>

<img src="docs/images/screenshot.png" alt="prayr Android app screenshot" width="340">

<br><br>

</div>

<hr>

<h2>✨ Features</h2>

<ul>
  <li>Prayer and Praise items with separate visual treatment.</li>
  <li>Mark an item as prayed for the current rotation.</li>
  <li>Manually mark an item as answered when it is no longer active.</li>
  <li>Random or sequential reminder selection.</li>
  <li>Random timing, fixed intervals, or grouped delivery at a chosen time.</li>
  <li>Per-item reminder controls including specific-time and repeating schedules.</li>
  <li>Configurable quiet hours and snooze duration.</li>
  <li>Driving mode with a Mute reminder action.</li>
  <li>Light, dark, and system appearance with multiple colour themes.</li>
  <li>Local <code>.pyr</code> backup, restore, sharing, and import.</li>
  <li>Local-first storage with no account required.</li>
</ul>

<h2>🚗 Android Auto</h2>

<p>
  prayr includes a spoken-audio media companion using Android Auto's media architecture.
  Android Auto renders the in-car interface, and available controls can vary by vehicle.
</p>

<p>
  The project declares itself as media because it provides real spoken playback. It does not
  impersonate messaging, calendar, or another application category.
</p>

<h2>🔨 Build locally</h2>

<p>Requirements:</p>

<ul>
  <li>JDK 17 or newer</li>
  <li>Android SDK Platform 35</li>
  <li>Android SDK Build Tools 35.0.0</li>
</ul>

<pre><code>export ANDROID_SDK_ROOT=/path/to/Android/Sdk
./build-local.sh</code></pre>

<p>The installable APK is written to:</p>

<pre><code>dist/prayr-v1.2.0.apk</code></pre>

<p>
  By default the script creates a development signing key under <code>.local-signing/</code>.
  That directory is ignored by Git. For releases, provide your own stable signing key through
  the environment variables documented inside <code>build-local.sh</code> and keep that key outside the repository.
</p>

<h2>👨‍💻 Project</h2>

<table>
  <tr><th align="left">Item</th><th align="left">Value</th></tr>
  <tr><td>Project</td><td><code>prayr</code></td></tr>
  <tr><td>Author / maintainer</td><td><strong>blondothenerd</strong></td></tr>
  <tr><td>Package ID</td><td><code>dev.blondothenerd.prayr</code></td></tr>
  <tr><td>Repository</td><td><code>blondothenerd/prayr</code></td></tr>
  <tr><td>Current version</td><td><code>1.2.0</code></td></tr>
  <tr><td>Minimum Android</td><td>Android 8.0 / API 26</td></tr>
  <tr><td>License</td><td>MIT</td></tr>
</table>

<h2>🔐 Privacy</h2>

<p>
  Prayer content can be deeply personal. prayr is local-first and the current source contains no
  analytics SDK, advertising SDK, account system, or cloud synchronisation service.
</p>

<p>See <a href="PRIVACY.md">PRIVACY.md</a>.</p>

<h2>📷 Screenshot</h2>

<p>
  Place the public screenshot at <code>docs/images/screenshot.png</code>. Use sample data only;
  do not publish real prayer names or private prayer content.
</p>

<h2>🤝 Contributing</h2>

<p>Issues and pull requests are welcome. See <a href="CONTRIBUTING.md">CONTRIBUTING.md</a>.</p>

<h2>📄 License</h2>

<p>Released under the MIT License. See <a href="LICENSE">LICENSE</a>.</p>

<hr>

<div align="center">
  <p>Made by <strong>blondothenerd</strong> 🙏</p>
</div>
