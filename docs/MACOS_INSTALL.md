# Hướng dẫn Cài đặt & Khởi chạy DeskFlow trên macOS
# macOS Installation & Gatekeeper Launch Guide

Tài liệu này cung cấp hướng dẫn chính thức và minh bạch về cách cài đặt và phê duyệt ứng dụng **DeskFlow** trên hệ điều hành **macOS 13.0+ (Ventura, Sonoma, Sequoia)**.

This document provides transparent, official guidance on installing and authorizing the **DeskFlow** desktop application on **macOS 13.0+ (Ventura, Sonoma, Sequoia)**.

---

## 1. Tính minh bạch về Chứng chỉ Ký mã & Notarization (Transparency Notice)

- **Mã nguồn mở và CI/CD minh bạch**: Toàn bộ ứng dụng DeskFlow macOS được biên dịch tự động 100% từ mã nguồn Swift gốc trên hệ thống **GitHub Actions** của kho lưu trữ [HenryYT13/deskflow-app](https://github.com/HenryYT13/deskflow-app).
- **Chữ ký mã Ad-hoc (Unsigned / Ad-hoc Code Signature)**: Tác giả dự án là nhà phát triển độc lập và hiện **không sở hữu tài khoản Apple Developer Program trả phí ($99/năm)**. Vì vậy:
  - Ứng dụng được ký mã dạng **ad-hoc** (`codesign --sign -`) với bundle identifier chính thức `com.deskflow.Station` và file quyền `DeskFlow.entitlements`.
  - Ứng dụng **chưa được Apple Notarize** (công chứng bởi Apple).
- **Phản ứng của macOS Gatekeeper**:
  - Khi bạn tải file `.dmg` hoặc `.zip` về từ trình duyệt, macOS sẽ tự động gắn thuộc tính cách ly (`com.apple.quarantine`).
  - Khi mở ứng dụng lần đầu, Gatekeeper sẽ hiển thị thông báo:
    > *"Deskflow was blocked from use because it is not from an identified developer"*  
    > *(Deskflow bị chặn sử dụng vì không phải từ một nhà phát triển được xác định)*  
    hoặc:  
    > *"Apple cannot check it for malicious software"*

---

## 2. Nguyên tắc An toàn (Security Rules)

> [!IMPORTANT]
> - **CHỈ tải ứng dụng từ kênh phát hành chính thức**: [DeskFlow GitHub Releases](https://github.com/HenryYT13/deskflow-app/releases).
> - **TUYỆT ĐỐI KHÔNG tắt Gatekeeper toàn hệ thống**: Không bao giờ chạy các lệnh nguy hiểm như `sudo spctl --master-disable`. Điều này sẽ vô hiệu hóa hoàn toàn lá chắn bảo vệ của Mac đối với mọi phần mềm độc hại khác.
> - **KHÔNG cần xoá cách ly mù quáng**: Thay vì chạy các câu lệnh can thiệp sâu, hãy sử dụng cơ chế cấp phép từng ứng dụng (**Per-App Authorization**) được Apple tích hợp sẵn trong macOS.

---

## 3. Cách mở ứng dụng an toàn qua cơ chế "Open Anyway" của Apple

Đây là quy trình chuẩn được Apple tài liệu hóa để người dùng cho phép ứng dụng từ các nhà phát triển độc lập chạy an toàn:

### Bước 1: Kéo ứng dụng vào Applications
- Mở file `DeskFlow-<version>-macOS.dmg`.
- Kéo biểu tượng **Deskflow.app** vào thư mục **Applications**.
*(Nếu dùng file `DeskFlow-<version>-macOS.zip`, hãy giải nén và di chuyển `Deskflow.app` vào `/Applications`).*

### Bước 2: Thử mở ứng dụng lần đầu
- Nhấp đúp vào `Deskflow.app` trong thư mục Applications.
- Màn hình sẽ hiện hộp thoại cảnh báo của Gatekeeper. Nhấn nút **OK** (hoặc **Done**) để đóng cảnh báo.

### Bước 3: Cho phép mở trong Cài đặt Hệ thống (Open Anyway)
1. Mở **System Settings** (Cài đặt hệ thống) trên máy Mac của bạn.
2. Chọn mục **Privacy & Security** (Quyền riêng tư & Bảo mật) ở thanh menu bên trái.
3. Cuộn xuống phần **Security** (Bảo mật). Bạn sẽ thấy dòng thông báo:
   > *"Deskflow" was blocked from use because it is not from an identified developer.*
4. Nhấn vào nút **Open Anyway** (Vẫn mở).
5. Nhập mật khẩu quản trị máy Mac hoặc quét Touch ID khi được hỏi.
6. Hộp thoại xác nhận cuối cùng sẽ xuất hiện: nhấn **Open** (Mở).

🎉 **Xong!** macOS sẽ ghi nhớ quyết định của bạn. Từ lần mở thứ hai trở đi, bạn có thể khởi chạy DeskFlow bình thường trực tiếp từ Launchpad hoặc Spotlight mà không cần thao tác lại.

---

## 4. Phương pháp mở nhanh bằng Finder (macOS 13 & 14)

Đối với macOS Ventura và Sonoma:
1. Mở Finder và vào thư mục **Applications** (`/Applications`).
2. Giữ phím **Control** và bấm chuột (hoặc nhấp chuột phải) vào **Deskflow.app**.
3. Chọn **Open** (Mở) từ menu ngữ cảnh.
4. Trên hộp thoại cảnh báo hiện ra, bấm **Open** (Mở).

---

## 5. Xác thực Tính toàn vẹn của Bản phát hành (Integrity Verification)

Để đảm bảo file bạn tải về là nguyên bản và chưa bị can thiệp bởi bên thứ ba:

1. Tải file `SHA256SUMS-macOS.txt` đính kèm trong [GitHub Release](https://github.com/HenryYT13/deskflow-app/releases).
2. Mở ứng dụng **Terminal** trên Mac và kiểm tra mã băm SHA256:
   ```bash
   shasum -a 256 DeskFlow-*-macOS.dmg
   # hoặc:
   shasum -a 256 DeskFlow-*-macOS.zip
   ```
3. Đối chiếu chuỗi kết quả với mã SHA-256 được công bố trong file `SHA256SUMS-macOS.txt` hoặc ghi chú phát hành trên GitHub.

---

## 6. Hướng dẫn Dành cho Tương lai (Future Developer ID Setup)

Nếu dự án được cấp chứng chỉ **Apple Developer ID**:
1. Đưa chứng chỉ vào GitHub Secrets:
   - `APPLE_CERTIFICATE_BASE64`
   - `APPLE_CERTIFICATE_PASSWORD`
   - `APPLE_SIGNING_IDENTITY` (ví dụ: `Developer ID Application: Your Name (TEAMID)`)
   - `APPLE_API_KEY_BASE64`, `APPLE_API_KEY_ID`, `APPLE_API_ISSUER` (hoặc `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`)
2. Workflow GitHub Actions sẽ tự động kích hoạt chế độ **Hardened Runtime** (`--options runtime`), gửi công chứng tới dịch vụ Apple Notary và dán nhãn (`stapler staple`). Khi đó ứng dụng sẽ tự động vượt qua Gatekeeper mà không cần các bước trên.
