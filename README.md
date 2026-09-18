<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif">

# 📡 Host Master

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="Host Master Icon"/>
</p>

<p align="center">
  <strong>Turn your Android device into a local network server</strong><br/>
  Share files, host websites, and transfer data over your WiFi network
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" />
  <img src="https://img.shields.io/badge/Min%20SDK-21-blue" />
  <img src="https://img.shields.io/badge/Language-Java-orange?logo=java" />
  <img src="https://img.shields.io/badge/License-MIT-lightgrey" />
</p>

![GitHub last commit](https://img.shields.io/github/last-commit/abdurrahman101bd/HostMaster)
![GitHub issues](https://img.shields.io/github/issues-raw/abdurrahman101bd/HostMaster)
![GitHub repo size](https://img.shields.io/github/repo-size/abdurrahman101bd/HostMaster)
![GitHub stars](https://img.shields.io/github/stars/abdurrahman101bd/HostMaster?style=social)
![GitHub forks](https://img.shields.io/github/forks/abdurrahman101bd/HostMaster?style=social)


---

## ✨ Features

### 🌐 HTTP Server
- Browse and download files from any browser on the same WiFi
- Stream videos and audio directly in the browser
- Host static websites (`index.html` auto-runs)
- Share individual files or entire folders
- Directory listing with file type icons
- Light/Dark themed web interface with theme toggle
- Range request support for media seeking

### 📦 FTP Server
- Connect with any FTP client (FileZilla, MT Manager, Solid Explorer)
- Passive mode (PASV) and Extended Passive mode (EPSV) — works with Linux GUI clients
- File upload, download, rename, delete
- Anonymous or username/password authentication
- Read-only mode
- Idle timeout with auto-shutdown

### 🌍 Web Hosting
- Host full websites from your phone
- Sub-folder websites each auto-serve their own `index.html`
- CSS, JS, fonts, images all load correctly
- Manage multiple website folders with pin-to-top feature
- Expandable file tree view with fullscreen mode

### 🔒 Security
- Optional password protection (Basic Auth) — separate credentials for HTTP and FTP
- Sandboxed to your selected folder only — no path traversal outside it
- Local network only

### ⚙️ More
- Permission gate on first launch — grants storage, notifications, and battery exemption before you ever reach the app
- Auto-start server on app launch
- QR code for quick access
- Request logs with search and filter, saved per day
- Network traffic chart
- Dark / Light / Follow System theme (follows your phone by default)
- Custom fonts support
- Notification control with Restart and Stop actions, plus sound/vibration on start, stop, and client connect/disconnect

---

## 🚀 Getting Started

### Requirements
- Android 5.0+ (API 21)
- Same WiFi network on both devices

### Setup
1. Install the APK on your Android device
2. On first launch, grant the requested permissions (storage, notifications, battery) on the permission screen — the app won't let you in until they're all set
3. Tap **CONFIG** to select a folder or files to share
4. Tap the **power button** to start the server
5. Open the URL shown on any device on the same network

### HTTP Access
Open in any browser:
```
http://192.168.x.x:8080
```

### FTP Access
Use any FTP client with:
```
Host: 192.168.x.x
Port: 2221
Protocol: FTP (plain, not SFTP)
Encryption: None / Plain FTP
```

---

## 🏗️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java |
| IDE | AndroidIDE |
| HTTP Server | NanoHTTPD |
| FTP Server | Custom implementation (raw Java sockets) |
| UI | Material Design, custom views |
| QR Code | ZXing |
| Min SDK | API 21 (Android 5.0) |

---

## 🔐 Permissions

| Permission | Purpose |
|-----------|---------|
| `MANAGE_EXTERNAL_STORAGE` | Access all file types (Android 11+) |
| `READ_MEDIA_*` | Access media files (Android 13+) |
| `INTERNET` | Network communication |
| `FOREGROUND_SERVICE` | Keep server running in background |
| `POST_NOTIFICATIONS` | Server status notification |
| Battery Optimization Exempt | Prevent OS from killing background server |

---

## 🗺️ Roadmap

- [x] HTTP file server
- [x] FTP server
- [x] Web hosting mode (including nested sub-folder sites with working CSS/JS)
- [x] Dark/Light/Follow System theme
- [x] File picker with multi-select
- [x] FTP PASV/EPSV (Linux GUI client support)
- [x] First-launch permission gate
- [x] Notification Start/Stop/Restart controls
- [ ] Upload UI from browser
- [ ] Custom domain / mDNS

---

## 🚀 Installation

1. Download the APK from [Releases](https://github.com/abdurrahman101bd/HostMaster/releases)
2. Enable **"Install from unknown sources"** in your device settings
3. Open the APK and install
4. Launch **Host Master**

---

## 📝 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 📞 Contact

[![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/abdurrahman101bd)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/abdurrahman101bd)
[![Twitter](https://img.shields.io/badge/Twitter-1DA1F2?style=for-the-badge&logo=twitter&logoColor=white)](https://x.com/abdurrahman101b)
[![Facebook](https://img.shields.io/badge/Facebook-1877F2?style=for-the-badge&logo=facebook&logoColor=white)](https://www.facebook.com/abdurrahman101bd)

---

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 🐛 Bug Reports

Found a bug? Please [open an issue](https://github.com/abdurrahman101bd/HostMaster/issues) with:
- Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

<p align="center">
  Built with ❤️ on Android, for Android
</p>

---

<p align="center">
  <a href="#-HostMaster">Back to Top ⬆️</a>
</p>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif">