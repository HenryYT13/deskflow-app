# DeskFlow App - Nhật ký thay đổi (Changelog)

Toàn bộ lịch sử các bản phát hành và cập nhật quan trọng của hệ sinh thái ứng dụng DeskFlow (Android, Windows WinUI 3, Linux GTK4, macOS Swift).

---

## [v1.1.1] - 2026-11-13

### 🐛 Sửa lỗi (Fixes)
- **Linux: đổi trang ESP32 bị lỗi 405 `invalid_request`.** Client gọi sai route `/api/app/control/display/switch-view` và chỉ retry khi gặp 404. Giờ gọi đúng `/display/view` trước và retry cả 404 lẫn 405. Đây chính là lỗi trong ảnh chụp màn hình.
- **Linux: hàng preset bị bóp thành 1 ký tự mỗi dòng.** `Adw.ActionRow.add_suffix()` nhồi 5–7 nút lên một dòng, khiến "Bảng màu gợi ý" và "Color Presets" hiển thị dọc từng chữ cái. Chuyển sang `FlowBox` 4 ô/hàng.
- **Linux: trang "Chế độ tập trung" không mở được.** Trang này được dựng 2 lần và không có mục trong sidebar. Giờ có mục riêng và chỉ dựng một lần.
- **Linux: đồng bộ màn hình đang hiển thị với ESP32.** Thêm dòng "Màn hình đang hoạt động trên trạm" và tự cập nhật preview khi trạm đổi màn hình.
- **Trang đang hiện không đồng bộ ở cả 4 app.** Firmware giờ gửi `active_view` trong mỗi bản tin `status_update`, backend lưu lại và trả qua `/control/status`; app Android poll 3 giây/lần.
- **Đổi sang tiếng Anh nhưng UI vẫn tiếng Việt.**
  - Android: thêm cầu nối `AppStrings` dịch toàn bộ ~390 chuỗi hardcode trong Compose (kể cả chuỗi có nội suy `${...}`).
  - Windows: thêm `UiTranslator` duyệt visual tree và dịch ~130 nhãn hardcode trong `MainWindow.xaml`.
  - Linux: thêm `i18n.py` dịch toàn bộ widget tree (Adw rows, buttons, labels).
- **Linux: tiêu đề tab Công cụ và thẻ chọn màn hình vẫn tiếng Việt khi đổi sang EN.** Giờ được dịch lại theo ngôn ngữ.

### ✨ Tính năng mới (New Features)
- **Linux: phát file MIDI (.mid) trên còi ESP32** — chọn bài mẫu hoặc chọn file từ máy, kèm nút Dừng.
- **Android: đổi tên thiết bị đã liên kết** (nút bút chì cho từng thiết bị, không chỉ thiết bị hiện tại).
- **Android: chọn thiết bị hiển thị spec** — danh sách radio chọn máy cấp số liệu CPU/RAM/pin cho ESP32 và mọi app.
- **Android: tách "Hẹn giờ đếm ngược" khỏi "Danh ngôn AI"** thành hai mục riêng trong Công cụ.

---

## [v1.1.0] - 2026-11-13

### ✨ Tính năng mới (New Features)
- **ESP32: menu đổi màu đèn khi giữ nút `>>`.** Giữ `>>` mở bảng cài đặt phần cứng và đặt sẵn ở dòng **"MÀU ĐÈN"**; nhấn `PLAY` để mở 7 màu có sẵn (Trắng, Đỏ, Xanh lá, Xanh dương, Vàng, Tím, Xanh ngọc), `PREV/NEXT` chọn, `PLAY` áp dụng. Toàn bộ đèn vẫn dùng đúng **GPIO 2** như cũ.
- **Lưới chọn màu 4 ô/hàng** trên màn OLED để bố cục không còn bị dồn cục; menu chính thu gọn chiều cao cho đủ 8 dòng.
- **Nấc chỉnh độ sáng LED đổi 10% → 5%** mỗi lần nhấn `PREV/NEXT` trên trạm.
- **Android: khoá màn hình khi bật Focus Mode.** Overlay giờ phủ toàn màn hình và chặn thao tác, vẫn chạy nền qua foreground service (không dùng Device Admin).
- **Android: bảng chọn 7 màu đèn có sẵn**, xếp 4 ô mỗi hàng, khớp preset của firmware.
- **Windows/Linux: cover art Spotify** hiển thị ảnh bìa thật từ `cover_url`.
- **Windows/Linux: chọn ngôn ngữ màn hình ESP32 (VI/EN)** tách biệt với ngôn ngữ ứng dụng.
- **Linux: nhập chủ đề câu danh ngôn AI** thay vì luôn dùng chủ đề học tập mặc định.
- **Windows: 7 preset màu đèn** xếp 4 ô mỗi hàng, đúng màu firmware.

### 🐛 Sửa lỗi (Fixes)
- **TKB Windows không đồng bộ:** panel TKB giờ tự làm mới mỗi 20 giây khi đang mở, báo lỗi rõ khi tải thất bại, và nút "Hiển thị TKB lên ESP32" gửi kèm dữ liệu thời khoá biểu nên màn trạm không còn trống.
- **Backend gửi kèm TKB khi chuyển view `tkb`**, thay vì chỉ gửi `switch_view`.
- **Monitor stats bị 0, 0, 0 khi cấu hình từ Windows:** view `stats` lấy đúng số liệu máy báo qua HTTP telemetry thay vì đọc dict legacy rỗng.
- **RAM cũ (441%) vẫn hiển thị sau khi thoát app:** máy offline giờ trả 0 thay vì giữ giá trị cuối, và backend tự chuyển sang máy đang báo số liệu thật.
- **Windows gửi CPU/pin thật** qua `GetSystemTimes` / `GetSystemPowerStatus` thay vì hằng số.
- **Spotify volume không chỉnh được:** backend tự tìm thiết bị đang hoạt động và thử lại một lần thay vì im lặng bỏ qua.
- **Linux: đổi ngôn ngữ ứng dụng** giờ cập nhật ngay thanh điều hướng, không cần khởi động lại.
- **Linux: bỏ dòng `Theme: <theme>`** ở góc trên bên phải.
- **Linux: TKB hiển thị theo dạng thẻ ngày** giống bản macOS (badge đồng phục + hai cột sáng/chiều).
- **Windows: thứ tự thanh điều hướng** khớp macOS (Hệ thống → Phần cứng → TKB → Spotify → Báo thức → Công cụ → Cài đặt).

### 📦 Thay đổi cấu trúc
- **Firmware ESP32 chuyển sang thư mục `firmware-esp32/`** (trước là `firmware/`). Lệnh build mới:
  `arduino-cli compile --fqbn esp32:esp32:esp32 firmware-esp32/khkt`

---

## [v1.0.9] - 2026-11-13

### 🐛 Sửa lỗi (Fixes)
- **Windows đồng bộ telemetry thật với backend:** bỏ các giá trị giả cố định `CPU 14.5%`, `RAM 48%`, `pin 92%` gửi mỗi giây.
- Windows giờ đọc RAM máy thật bằng `GlobalMemoryStatusEx` trước khi gửi `/api/app/control/telemetry`; không còn tự báo sai số RAM máy tính.
- Clamp RAM server/máy tính về `0–100%` khi hiển thị, nên giá trị lỗi như `441%` không còn xuất hiện trên giao diện.
- Backend production đã kiểm tra trả RAM server hợp lệ (`43.9%` tại thời điểm kiểm tra).

---

## [v1.0.8] - 2026-11-12

### 🐛 Sửa lỗi (Fixes)
- **Windows: giải đúng bài toán nhưng cửa sổ vẫn khoá, không thoát được.**
  - Nguyên nhân: nút "Dừng phiên tập trung" và đoạn xử lý sau khi giải đúng đều gọi `POST /api/focus/enable` với `{"enabled": false}`. Backend chỉ đọc `duration_minutes` / `reason`, **bỏ qua `enabled`**, nên mỗi lần "dừng" lại tạo một phiên 25 phút mới → 10 giây sau app poll thấy active và khoá lại cửa sổ.
  - Fix backend: `/api/focus/enable` giờ trả **409 `challenge_required`** khi nhận `enabled: false`, không tạo phiên mới.
  - Fix app Windows: không bao giờ gửi lệnh "stop" giả nữa. Giải đúng bài toán là backend tự tắt phiên, app chỉ cần mở khoá cục bộ.
  - Thêm cửa sổ 20 giây chống khoá lại (`FocusLockPolicy.ShouldApplyPolledState`) để một lần poll gửi trước lúc mở khoá không khoá lại cửa sổ.
  - Nếu phiên đã hết hạn / bị tắt từ thiết bị khác mà vẫn bấm giải, app tự mở khoá thay vì báo lỗi.
  - Bài toán mở khoá được tải sẵn ngay khi khoá và tự cuộn vào tầm nhìn; nhấn **Enter** trong ô đáp án cũng kiểm tra được.
  - Thêm mục "Focus Mode" trong thanh điều hướng để mở thẳng khu vực này.

---

## [v1.0.7] - 2026-11-12

### ✨ Tính năng mới (New Features)
- **Đồng bộ phần cứng ESP32 thật sự trên cả 4 ứng dụng (Android · Windows · macOS · Linux):**
  - **Bỏ hoàn toàn thanh "Độ sáng màn hình OLED/ST7789".** Đèn nền ST7789 nối cứng 3.3V nên phần mềm không điều khiển được; backend cũng đã bỏ nhận `screen` để không còn gửi lệnh vô nghĩa xuống trạm.
  - **Độ sáng LED giờ là % (0–100%).** macOS từng hiển thị `/255` và gửi giá trị 0–255 xuống backend (bị kẹp về 100%), giờ đã đúng thang %.
  - **Bật / Tắt đèn rõ ràng.** Trước đây chỉ tắt được. Thêm nút **Bật đèn** gửi `{"action":"led","act":"on"}` và firmware xử lý `on` / `off` / `toggle` / `blink`.
  - **Thêm preset màu `#FFC7AC` (trắng ấm / yellowish white)** vào bảng màu nhanh của cả 4 app.
- **Âm báo thức riêng trên ESP32:** file mới `alarm_tune.cpp/.h` với giai điệu "DeskFlow Alert" (2 hợp âm tăng dần + nốt giữ), chạy non-blocking và lặp tới khi giải đúng bài toán. Không còn dùng tiếng bíp 2600Hz cũ; đèn LED nháy đỏ 420ms đồng bộ theo nhịp.
- **Mã Morse mặc định: 1000 Hz và chấm 60 ms** trên firmware, backend và cả 4 app (trước là 2400 Hz / 80 ms).
- **Song ngữ Việt – Anh cho trạm ESP32:** thêm `i18n.cpp/.h`, mọi menu và màn hình đều có bản tiếng Anh, bao gồm cả menu `>>` (Hardware Settings).
- **Menu `>>` trên ESP32 có thêm âm lượng Spotify:** dòng "SPOTIFY VOLUME" chỉnh ±5%, đẩy qua MQTT `deskflow/button_action` (`volume_up` / `volume_down`); backend gọi Spotify API và phát lại giá trị thật xuống OLED + mọi app để luôn khớp.
- **Portal web của trạm:** thêm nút Bật/Tắt đèn, slider độ sáng LED %, nút ±5% âm lượng Spotify và nút chuyển VI/EN.

### 🔧 Sửa lỗi (Fixes)
- macOS gửi độ sáng LED sai thang (0–255) khiến đèn luôn ở 100%.
- Android mất state khi đổi tab do `screenBrightness` còn sót lại sau khi bỏ UI độ sáng màn hình.
- Backend không còn nhận `screen_bright` trong `/api/app/control/lamp/dim` (tham số vẫn được bỏ qua an toàn cho app bản cũ).

### ✅ Kiểm thử (Tests)
- Firmware biên dịch thành công bằng `arduino-cli` với `esp32:esp32@3.3.12` (90% flash, 16% RAM).
- macOS: `swiftc` compile toàn bộ source, không lỗi.
- Linux: 21 unit test pass; `py_compile` toàn bộ module.
- Android: cập nhật test `setLampDim(60)` theo chữ ký mới.

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
