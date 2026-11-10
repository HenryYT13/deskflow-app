# DeskFlow Client Apps & Hardware Station

Kho lưu trữ ứng dụng máy khách đa nền tảng (**Android, Windows, Linux, macOS**) và firmware trạm phần cứng (**ESP32 Station**) của hệ sinh thái **DeskFlow**.

Tất cả ứng dụng đều kết nối đồng bộ thời gian thực với **DeskFlow Backend** để điều khiển chế độ tập trung (Focus Mode), bài toán thử thách chống gian lận, giấy nhớ, báo thức đếm ngược và hiển thị trạng thái bàn học thông minh.

---

## 📥 Tải về Bản Cài Đặt (GitHub Releases)

Tất cả các bản dựng mới nhất được tự động biên dịch và phát hành tại:  
👉 **[DeskFlow App Releases](https://github.com/HenryYT13/deskflow-app/releases)**

| Hệ điều hành | Định dạng file | Mô tả |
|---|---|---|
| **Windows 10/11** | 🪟 `DeskFlow-1.0.0-windows-setup.exe`<br>🪟 `DeskFlow-1.0.0-win-x64.zip` | Bộ cài đặt Inno Setup Wizard hoặc bản portable zip tự chạy (WinUI 3 x64 self-contained). |
| **Linux** | 🐧 `deskflow_1.0.0_amd64.deb`<br>🐧 `deskflow-1.0.0-1.x86_64.rpm`<br>🐧 `DeskFlow-1.0.0-x86_64.AppImage`<br>🐧 `DeskFlow-1.0.0-linux-x86_64.tar.gz` | Đầy đủ định dạng cho Ubuntu/Debian (.deb), Fedora/RHEL (.rpm), file chạy độc lập (.AppImage) và tarball. |
| **macOS** | 🍏 `Deskflow-1.0.0-macOS.dmg` | Bộ cài đặt DMG kéo thả cho macOS 13.0+ (Ventura, Sonoma, Sequoia). |
| **Android** | 📱 `DeskFlow-android.apk` | Ứng dụng Android 8.0+ (hỗ trợ quét mã QR, Display Over Apps). |

---

## 📱 Chi tiết các nền tảng

### 1. Android (`android-app/`)
Ứng dụng di động trung tâm được xây dựng bằng **Kotlin** + **Jetpack Compose** + **Material 3**:
- **Liên kết thiết bị (Device Pairing)**:
  - Quét mã QR tự động bằng camera (ZXing Barcode Scanner) để nhận diện mã ghép nối 8 ký tự.
  - Hỗ trợ nhập mã thủ công do Discord `/app pair` tạo ra.
- **Hiển thị đè lên ứng dụng khác (Display Over Other Apps)**:
  - Khi bật Focus Mode, ứng dụng tự động hiển thị thanh thông báo đè trên toàn màn hình (`SYSTEM_ALERT_WINDOW`) nhắc nhở tập trung và ngăn phân tâm.
  - Nút cấp quyền tiện lợi trong mục Cài đặt.
- **Đổi tên thiết bị**: Đổi tên thiết bị trực tiếp trên giao diện để quản lý dễ dàng trong hệ sinh thái.

### 2. Windows (`win/`)
Ứng dụng máy tính gốc viết bằng **C# .NET 8** và giao diện **WinUI 3 (Windows App SDK)**:
- Giao diện Fluent Design hiện đại, bo tròn góc chuẩn Windows 11.
- Quản lý đồng bộ: Thêm/xoá báo thức, ghi chú giấy nhớ (Sticky Notes), đếm ngược kỳ thi.
- Đổi tên thiết bị trực tiếp.
- Cơ chế ghi log sự cố tự động vào `%LOCALAPPDATA%\DeskFlow\crash.log` để dễ dàng chẩn đoán lỗi.

### 3. Linux (`linux/`)
Ứng dụng máy tính GNOME viết bằng **Python 3** + **GTK4** + **Libadwaita**:
- Tuân thủ hướng dẫn thiết kế GNOME Human Interface Guidelines (HIG).
- Đồng bộ giao diện sáng/tối tự động với hệ thống.
- Hỗ trợ đầy đủ bộ công cụ: Giấy nhớ, Báo thức, Bộ chuyển đổi Mã Morse, Pomodoro.
- Kiểm thử tích hợp tự động qua cờ lệnh `./linux/bin/deskflow --self-test`.

### 4. macOS (`macos/`)
Ứng dụng Apple gốc viết bằng **Swift 5.9** + **SwiftUI**:
- Tương thích macOS 13.0 trở lên.
- **Bản địa hoá tiếng Việt 100%**: Tất cả các tab, nút bấm, thông báo đều hỗ trợ tiếng Việt đầy đủ.
- Giao diện Split-View gọn gàng, chia khu vực làm việc trực quan.
- Tự động đóng gói thành `.dmg` hoàn chỉnh kèm hình nền cài đặt kéo thả.

### 5. ESP32 Station Firmware (`firmware/khkt/`)
Firmware trạm điều khiển phần cứng viết bằng **Arduino C++** cho board **ESP32-WROOM-32**:
- **Màn hình OLED 0.96" SSD1306**: Hiển thị đồng hồ, phiên Pomodoro, bài hát Spotify đang phát, kỳ thi đếm ngược.
- **Thanh LED RGB NeoPixel**: Hiệu ứng ánh sáng theo trạng thái làm việc (xanh dương khi tập trung, xanh lá khi nghỉ, đỏ khi báo thức).
- **Cảm biến nhiệt độ & độ ẩm DHT11/DHT22**: Giám sát điều kiện phòng học và gửi dữ liệu về máy chủ qua MQTT.
- **Trình phát Melody MIDI**: Trình phát giai điệu MIDI đơn âm tích hợp trên còi buzzer (kích hoạt qua lệnh MQTT `melody_play` và `melody_stop`).

---

## 🛠️ Hướng dẫn Tự Build từ Mã Nguồn

### Android App
```bash
cd android-app
# Cần cài đặt JDK 17 và Android SDK
./gradlew assembleDebug
# File APK xuất ra tại: app/build/outputs/apk/debug/app-debug.apk
```

### Linux App
```bash
# Cài đặt thư viện GTK4 & đóng gói (Ubuntu/Debian)
sudo apt update && sudo apt install -y python3-gi gir1.2-gtk-4.0 gir1.2-adw-1 rpm binutils
# Chạy ứng dụng trực tiếp
./linux/bin/deskflow

# Hoặc tự tạo trọn bộ gói cài đặt (.deb, .rpm, .AppImage, .tar.gz):
./linux/packaging/build_packages.sh
```

### Windows App
Yêu cầu Windows 10/11 có cài đặt .NET 8 SDK và Inno Setup 6 (để tạo file cài đặt setup .exe):
```powershell
cd win
# Chạy script tự động build và đóng gói
powershell -ExecutionPolicy Bypass -File .\scripts\build_windows.ps1
```

### macOS App
Yêu cầu máy Mac chạy macOS 13+ có cài Xcode / Command Line Tools:
```bash
cd macos
# Build ứng dụng
swift build -c release

# Hoặc tạo bộ cài đặt DMG:
./scripts/build_macos_dmg.sh
```

### ESP32 Firmware
Sử dụng **Arduino IDE** (chọn board `ESP32 Dev Module`) hoặc **arduino-cli**:
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/khkt
```
Các thư viện cần cài đặt: `Adafruit GFX Library`, `Adafruit SSD1306`, `Adafruit NeoPixel`, `DHT sensor library`, `PubSubClient`, `ArduinoJson`.

---

## 🔄 Quy trình Tự động Hoá Phát hành (CI/CD)

Mỗi khi đẩy một Git Tag mới (ví dụ `git tag v1.0.2 && git push origin v1.0.2`):
1. Runner `ubuntu-latest` biên dịch Android APK và trọn bộ 4 định dạng gói Linux.
2. Runner `windows-latest` biên dịch WinUI 3 x64 và dùng Inno Setup đóng gói `DeskFlow-windows-setup.exe` cùng file `.zip`.
3. Runner `macos-latest` biên dịch mã nguồn Swift và đóng gói `DeskFlow-macOS.dmg`.
4. Job tổng hợp tự động gom tất cả file tải về và tạo **GitHub Release** công khai cho người dùng.
