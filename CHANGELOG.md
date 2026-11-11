# DeskFlow App - Nhật ký thay đổi (Changelog)

Toàn bộ lịch sử các bản phát hành và cập nhật quan trọng của hệ sinh thái ứng dụng DeskFlow (Android, Windows WinUI 3, Linux GTK4, macOS Swift).

---

## [v1.0.4] - 2026-11-11

### ✨ Tính năng mới (New Features)
- **Tùy chỉnh tên thiết bị khi tạo mã QR ghép nối trên Desktop (WinUI 3 & Linux):**
  - Cho phép người dùng nhập trực tiếp tên thiết bị hiển thị mong muốn (ví dụ: *PC Phòng Khách*, *Laptop Asus*, v.v.) ngay trên giao diện thẻ quét QR trước khi tạo mã.
  - Mã QR tự động mang payload `deskflow://pair?name=...` giúp ứng dụng Android nhận diện đúng tên thiết bị tùy chỉnh để phê duyệt.
- **Xóa & hủy liên kết thiết bị trực tiếp trên ứng dụng Android DeskFlow:**
  - Nút biểu tượng thùng rác trong danh sách thiết bị cho phép người dùng xóa bất kỳ thiết bị nào khỏi tài khoản ngay lập tức với hộp thoại xác nhận an toàn.
  - Hỗ trợ xóa thiết bị khác và hủy liên kết ngay trên thiết bị hiện tại mà không bị chặn phân quyền (403 Forbidden).
  - Cập nhật danh sách thiết bị tức thời trên giao diện (optimistic update).
- **Trình phát giai điệu mẫu 8-bit (Preset Melody Jukebox) trực tiếp trên DeskFlow App:**
  - Hỗ trợ phát nhanh các giai điệu 8-bit kinh điển qua loa còi ESP32: *Super Mario Bros Theme*, *Never Gonna Give You Up (Rickroll)*, *Tetris Korobeiniki*, *Astronomia (Coffin Dance)*, *Star Wars Imperial March*, *Nokia Tune Gran Vals*.
  - Tích hợp nút phát, dừng phát còi khẩn cấp và bộ chọn tệp MIDI mở rộng.

### 🐛 Vá lỗi & Cải thiện (Bug Fixes & Improvements)
- **Sửa lỗi không chỉnh được độ sáng đèn LED (WS2812B NeoPixel) qua ứng dụng:**
  - Bổ sung bộ xử lý lệnh MQTT `action: "dim"` trên firmware ESP32 (`khkt.ino`), cập nhật ngay lập tức `ledBrightness` (0–100%) và lưu vào NVS flash `prefs`.
  - Đồng bộ `led_brightness` và `led_bright` chuẩn hóa hai chiều giữa Backend, App và Station.
- **Nâng cấp bộ nhớ giải mã JSON trên ESP32 cho MIDI dài:**
  - Tăng kích thước bộ đệm `DynamicJsonDocument` lên 32KB để các tệp MIDI chứa hàng trăm nốt (như Pink Panther 295 nốt) không bị tràn bộ nhớ hoặc im lặng khi phát.
- **Tối ưu hóa âm học Buzzer:**
  - Duy trì kỹ thuật ngắt nốt gate-time articulation (85% phát, 15% khoảng lặng staccato) giúp phân tách rõ ràng các nốt nhạc cùng cao độ.

---

## [v1.0.3] - 2026-11-11
- Thêm cổng ghép nối Dynamic QR Pairing Gate trên Desktop & quét QR trên Android.
- Màn hình Thời khóa biểu (TKB) chuyên biệt hiển thị lịch học và đồng phục.
- Bộ chọn màu Accent Color thời gian thực cho màn hình ESP32.
- Lớp phủ chống xao nhãng toàn màn hình (Full-screen Blocker) trên Android trong phiên Focus Mode.

---

## [v1.0.2] - 2026-11-08
- Tích hợp điều khiển đèn LED thông minh và chế độ tối tự động (Scheduled Dark Mode).
- Quản lý danh sách ghi chú Sticky Notes và đếm ngược kỳ thi (Exam Countdown).

---

## [v1.0.1] - 2026-11-04
- Đồng bộ bài hát và lời bài hát theo thời gian thực từ Spotify qua MQTT.
- Hỗ trợ bộ gõ mã Morse và chuyển đổi văn bản sang tín hiệu còi chíp.

---

## [v1.0.0] - 2026-10-25
- Phát hành phiên bản đầu tiên của DeskFlow App trên Android, Windows, Linux và macOS.
