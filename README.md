# AutoCountMoney

Ứng dụng Android Kotlin/Compose ghi nhận khoản chi từ thông báo MoMo/ZaloPay, MB Bank, TPBank và Vietcombank, nhắc nhập lý do và danh mục, lưu bằng Room trên thiết bị.

## Chức năng bản test

- Tổng quan tổng chi, giao dịch chờ bổ sung, số dư gần nhất nếu thông báo cung cấp.
- Danh sách giao dịch, lọc chờ bổ sung/đã lưu, sửa lý do và danh mục.
- Thiết lập quyền đọc thông báo, gửi lời nhắc và hiển thị popup trên ứng dụng khác; bật/tắt từng ngân hàng/ví.
- Mô phỏng cả 5 nguồn qua parser với khoản chi 50.000 ₫, có hoặc không có số dư; dữ liệu test tách khỏi dữ liệu thật.
- Popup nền có Lưu/Để sau, hàng đợi một form mỗi lần. Nếu không có quyền popup, mở giao dịch từ thông báo hoặc danh sách.

## Cách test trên điện thoại

1. Mở project trong Android Studio, Sync Gradle và Run module `app`.
2. Bấm **Mô phỏng giao dịch** tại Tổng quan: nhập lý do, chọn danh mục, Lưu. Kiểm tra giao dịch ở tab Mô phỏng và số dư 1.200.000 ₫.
3. Bỏ chọn **Thông báo có số dư**, test lại: ứng dụng phải báo không có số dư.
4. Chọn **Để sau**, kiểm tra mục Chờ bổ sung, mở lại và hoàn tất. Đóng/mở app để kiểm tra lưu bền vững.
5. Trong Thiết lập, cấp quyền **Gửi lời nhắc** và **Popup trên ứng dụng khác**.
6. Bấm **Test popup sau 5 giây**, bấm Home ngay, giữ màn hình mở khóa. Form nền xuất hiện nếu có quyền overlay; nếu không, kiểm tra thông báo hoặc danh sách Mô phỏng.
7. Cấp **Đọc thông báo ngân hàng/ví**, bật nguồn đang dùng, bật thông báo giao dịch trong ứng dụng ngân hàng/ví. Đối chiếu một thông báo giao dịch thực tế với dữ liệu ghi nhận.

Không cần đăng nhập ngân hàng/ví trong AutoCountMoney. Chế độ mô phỏng tạo dữ liệu trực tiếp và không kiểm tra được việc hệ thống cấp Notification Access. Không có foreground service chạy liên tục; listener do Android quản lý.

## Giới hạn hiện tại

Parser là bộ quy tắc thử nghiệm, chưa đối chiếu thông báo thực tế của người dùng. Nhận các cấu trúc rõ như `Thanh toán thành công 50.000đ. Số dư ví: 1.200.000đ`, bỏ qua dữ liệu thiếu/không rõ số tiền, quảng cáo, OTP, nạp/nhận/hoàn/rút tiền và giao dịch thất bại. Các mẫu khác cần bổ sung fixture trước khi hỗ trợ; chuyển tiền nội bộ có thể chưa phân biệt được với khoản chuyển đi. Thông báo chỉ hiện trong Zalo không nằm trong phạm vi đọc.

Số dư là dữ liệu từ thông báo, không phải truy vấn số dư ví trực tiếp. Không tự trừ tiền từ số dư cũ, vì khoản thanh toán có thể dùng ngân hàng liên kết. Chống trùng dựa vào notification key, thời điểm sự kiện và amount, với unique index trong Room; chưa có mẫu mã giao dịch ví để chống trùng qua nhiều notification khác nhau cho cùng giao dịch.

Bản này dùng database `wallet-tracker.db`, giữ nguyên file `app-db` của bản thử nghiệm trước. Dữ liệu không được tự động import từ schema cũ; không có thao tác xóa database cũ. Các schema mới được KSP xuất vào `app/schemas` khi build; các thay đổi schema sau này cần migration.

## Kiểm tra phát triển

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Nếu cần Gradle user home trong workspace:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Kết quả trong môi trường chỉnh sửa: 6 test parser được biên dịch và chạy độc lập bằng Kotlin/JUnit trong cache, đều pass. Kiểm tra kiểu source Kotlin bằng dependency Android trong cache đã thực hiện; chưa thay thế build APK/Compose/KSP. Build Gradle đầy đủ bị chặn khi tải JDK 25 theo `gradle/gradle-daemon-jvm.properties`, và workspace cache chưa có đủ dependency. Chưa kiểm tra popup/UI trên thiết bị.

## Ngân hàng mới

Đã thêm quy tắc thử nghiệm cho MB Bank, TPBank và Vietcombank. Xem [nghiên cứu, phạm vi và hướng dẫn kiểm tra](docs/bank-notifications.md). Fixture là mẫu tổng hợp, chưa xác minh push thật. 21 test parser đã pass bằng Kotlin/JUnit độc lập; build APK chưa hoàn tất do môi trường không tải được Gradle.

