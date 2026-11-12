# DeskFlow App - Nhật ký thay đổi (Changelog)

Toàn bộ lịch sử các bản phát hành và cập nhật quan trọng của hệ sinh thái ứng dụng DeskFlow (Android, Windows WinUI 3, Linux GTK4, macOS Swift).

---

## [v1.0.6] - 2026-11-12

### ✨ Tính năng mới (New Features)
- **Focus Mode khoá cứng trên cả 3 bản desktop (Windows WinUI 3 · macOS SwiftUI · Linux GTK4):**
  - Khi phiên tập trung đang chạy, cửa sổ DeskFlow luôn nổi trên các ứng dụng khác.
  - Chặn đóng cửa sổ và chặn thoát ứng dụng (`Alt+F4`, `Cmd+Q`, `Ctrl+Q`, nút X, menu Quit).
  - Cách thoát duy nhất là giải đúng bài toán mở khoá (`/api/focus/challenge` → `/api/focus/verify`) hoặc để phiên tự hết giờ. Trạng thái khoá được đồng bộ lại từ backend khi mở app hoặc khi phiên kết thúc từ thiết bị khác.
  - **Windows:** dùng `SetWindowPos(HWND_TOPMOST)`, `OverlappedPresenter.IsClosable/IsMinimizable`, sự kiện `AppWindow.Closing` và bộ đếm kiểm tra `/api/focus/status` mỗi 10 giây.
  - **macOS:** đặt `NSWindow.level = .floating`, vô hiệu hoá nút đóng/thu nhỏ, chặn `⌘Q ⌘W ⌘M ⌘H` bằng event monitor và trả `.terminateCancel` từ `applicationShouldTerminate`.
  - **Linux:** chặn `close-request`, chặn action `app.quit` (`Ctrl+Q`), và gắn `_NET_WM_STATE_ABOVE` trên X11. Trên Wayland, compositor không cho ứng dụng tự ép luôn nổi nên chỉ phần khoá đóng/thoát là chắc chắn hoạt động.
- **Thời lượng Focus Mode tuỳ chỉnh trên cả 4 ứng dụng (Android · Windows · macOS · Linux):**
  - Nhập số phút bất kỳ từ 1 đến 300, ví dụ **2 phút** hoặc 120 phút, thay vì chỉ chọn 25/50 phút.
  - **Android:** thêm ô "Tùy chỉnh số phút" bên cạnh các chip nhanh (có thêm chip 2m).
  - **Windows:** thêm `NumberBox` 1–300 và nút "Bắt đầu với số phút này".
  - **macOS:** thêm ô nhập số phút + Stepper 1–300 và nút "Bắt đầu X phút", vẫn giữ phím nhanh 25m/50m.
  - **Linux:** đổi `Adw.SpinRow` từ 5–180 bước 5 thành 1–300 bước 1.
  - Backend đã chấp nhận mọi `duration_minutes > 0` nên không cần sửa máy chủ.

### ✅ Kiểm thử (Tests)
- Thêm unit test cho `FocusLockPolicy` (Windows), `FocusWindowLock` (macOS) và `FocusLockState` (Linux).

---

## [v1.0.5] - 2026-11-11

### ✨ Tính năng mới & Trải nghiệm (New Features & UX)
- **Điều chỉnh độ sáng đèn LED & màn hình theo thời gian thực (Real-time Continuous Slider Tracking):**
  - Thanh trượt (Slider) trên ứng dụng Android phản hồi ngay lập tức theo từng nấc di chuyển của ngón tay, không cần phải nhấc tay/thả thanh trượt mới gửi lệnh.
  - Tối ưu hóa điều tiết luồng sự kiện (coroutine throttle/debounce 50ms) giúp trải nghiệm vuốt chỉnh mượt mà, không giật lag và chống nghẽn đường truyền mạng.
  - Backend tăng giới hạn tần suất yêu cầu lên 1200 req/phút và trì hoãn ghi đĩa cài đặt để phục vụ điều khiển phần cứng liên tục.
- **Tạo mã QR ghép nối trực tiếp trong Discord Bot:**
  - Lệnh Discord `/app pair`, `/pair`, `/qr` tự động xuất ảnh mã QR đính kèm trực tiếp trong tin nhắn để người dùng quét ngay bằng điện thoại.

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
