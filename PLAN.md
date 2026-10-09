# Kế hoạch AutoCountMoney

Ngày kiểm tra: 08/10/2026. Phạm vi hiện tại: kiểm tra project và lên kế hoạch, chưa triển khai tính năng.

Cập nhật sau yêu cầu triển khai: đã sửa khai báo Room/DAO, cấu hình KSP/compiler và driver; thêm repository, giao diện Tổng quan/Giao dịch/Thiết lập, form nhập/sửa lý do và danh mục, mô phỏng tách dữ liệu thật, lời nhắc và overlay nền. Parser MoMo/ZaloPay đang ở mức quy tắc thử nghiệm, 6 test độc lập pass. Chưa build APK/KSP đầy đủ do môi trường không tải được JDK/dependency; chưa đối chiếu thông báo ví thật hay kiểm tra UI trên thiết bị. Xem `README.md` để test. Các mục dưới đây giữ vai trò yêu cầu nghiệm thu, không phải xác nhận mọi bước đã được kiểm chứng.

## 1. Mục tiêu và phạm vi MVP

Ứng dụng Android nhận thông báo giao dịch của MoMo/ZaloPay để test trước; mở rộng ngân hàng sau. Khi nhận diện được khoản thanh toán/chuyển tiền đi thành công, lưu giao dịch chờ bổ sung rồi hiện form nhập lý do và chọn danh mục. Sau khi lưu, hiển thị số dư sau giao dịch nếu đọc được từ thông báo.

- Giữ Kotlin + Jetpack Compose + Room; xử lý và lưu dữ liệu trên thiết bị.
- Bản test ưu tiên MoMo, thêm ZaloPay khi có mẫu thông báo thực tế; cùng một kiến trúc parser cho ví/ngân hàng.
- Nguồn thông báo: MoMo `com.mservice.momotransfer`, ZaloPay `vn.com.vng.zalopay` (đối chiếu Google Play, kiểm tra lại trên máy test). Không đọc toàn bộ thông báo Zalo để suy ra giao dịch ZaloPay.
- MVP dùng VND, số tiền nguyên lưu bằng `Long`; không dùng `Float`/`Double` cho tiền.
- Chưa bao gồm SMS, API ngân hàng, đăng nhập ngân hàng hay đồng bộ cloud.
- Danh mục ban đầu: Ăn uống, Di chuyển, Mua sắm, Hóa đơn, Giải trí, Sức khỏe, Khác.
- Không đảm bảo thu thập được giao dịch khi thông báo ngân hàng bị tắt, bị ẩn nội dung, quyền bị thu hồi hoặc hệ điều hành không cho listener hoạt động.

## 2. Hiện trạng và việc cần sửa trước

| Thành phần | Hiện trạng | Việc cần làm |
| --- | --- | --- |
| `MainActivity.kt` | Xin quyền thông báo; `ExpenseScreen` không có nội dung chạy | Onboarding, trạng thái quyền, danh sách và form giao dịch |
| `BankNotificationListener.kt` | Đọc title/text rồi ghi log của mọi ứng dụng | Lọc package, chuẩn hóa nội dung, parser và lưu giao dịch; bỏ log nội dung riêng tư |
| `Transaction.kt` | Có ngân hàng, amount, content, isIncome, timestamp | Bổ sung tài khoản, số dư nullable, lý do, danh mục, trạng thái và chống trùng |
| `TransactionDao.kt` | Query dùng bảng `Transaction` trong khi entity là `bank_transactions` | Sửa tên bảng; thêm cập nhật, truy vấn pending, sắp xếp và tổng chi |
| `AppDatabase.kt` | Chưa kế thừa `RoomDatabase` | Sửa khai báo, cấu hình sinh code/schema và migration khi cần |
| Gradle | Có Room 3 runtime/common, chưa có KSP/compiler | Cấu hình KSP và Room 3 compiler phù hợp với toolchain hiện có |
| Manifest | Đã khai báo listener và `POST_NOTIFICATIONS` | Luồng cấp Notification Access; thêm quyền overlay cho popup |
| Test | Chỉ có test mẫu | Test parser, chống trùng và cập nhật dữ liệu |

Đã thử `gradlew.bat :app:assembleDebug --offline`, nhưng Gradle dừng trước bước biên dịch vì không tạo được lock file tại `C:\.gradle\wrapper\dists`. Chưa xác nhận project build được; các lỗi ở trên là kết quả đọc source. Cần chạy build với Gradle user home có quyền ghi và dependency/SDK sẵn có. Không tự đổi phiên bản SDK/AGP chỉ vì môi trường kiểm tra thiếu cache.

## 3. Luồng người dùng

1. Mở app lần đầu, chọn MoMo/ZaloPay đã cài và bật Notification Access trong Settings. Quyền này khác quyền gửi thông báo.
2. Xin quyền gửi thông báo trên Android 13+ với giải thích trong onboarding.
3. Cho bật chế độ popup và hướng dẫn cấp quyền hiển thị trên ứng dụng khác; kiểm tra lại quyền khi trở về app.
4. Listener nhận thông báo → lọc MoMo/ZaloPay → loại thông báo nhóm/quảng cáo/OTP → đọc nội dung → parse trạng thái thành công và hướng tiền → chống trùng.
5. Lưu giao dịch `PENDING` trong Room trước khi hiện giao diện. Chỉ giao dịch trừ tiền mới nhắc nhập lý do.
6. Nếu app đang mở: hiện form trong app. Nếu đang ở nền, màn hình mở khóa và có quyền overlay: hiện một popup. Các giao dịch tiếp theo nằm trong hàng đợi.
7. Nếu không thể hiện overlay hoặc máy đang khóa: gửi thông báo có hành động mở form qua `PendingIntent` trỏ trực tiếp tới Activity. Nếu quyền thông báo cũng bị từ chối, giao dịch vẫn xuất hiện trong mục chờ bổ sung khi mở app.
8. Form hiển thị ngân hàng, tài khoản đã che, số tiền trừ và thời điểm; nhập lý do, chọn danh mục, bấm **Lưu** hoặc **Để sau**. Lưu yêu cầu lý do không rỗng và một danh mục.
9. Sau khi lưu: đánh dấu `COMPLETED`, đóng nhắc tương ứng, hiện xác nhận cùng số dư sau giao dịch nếu có. Để sau giữ `PENDING` và không liên tục mở lại popup cho cùng giao dịch.

Ví dụ dữ liệu mô phỏng: `GD: -50,000 VND. So du: 1,200,000 VND` → form `Chi 50.000 ₫` → lý do `Ăn trưa`, danh mục `Ăn uống` → xác nhận `Đã lưu · Số dư sau giao dịch: 1.200.000 ₫`.

## 4. Cách đọc thông báo và số dư

- Mỗi ngân hàng có parser riêng, chọn bằng package name đã xác minh; không nhận diện chỉ dựa vào tên ngân hàng trong text của app bất kỳ.
- Bản đầu viết `MoMoParser` và `ZaloPayParser` trong giao diện chung `TransactionNotificationParser`; chỉ xác nhận mẫu đã có fixture thực tế. Chưa có mẫu thì cung cấp chế độ test nội bộ với dữ liệu mô phỏng, không tuyên bố đọc chính xác thông báo ví thật.
- Ví điện tử: thanh toán/chuyển tiền đi thành công được nhắc nhập; nhận tiền/hoàn tiền/nạp ví không tính là chi tiêu; giao dịch thất bại/đang xử lý không ghi thành khoản chi thành công. Rút tiền và chuyển nội bộ cần phân loại là chuyển tiền để tránh làm sai tổng chi.
- Tách số tiền giao dịch khỏi số tiền ưu đãi/cashback/hạn mức. Nếu không rõ nguồn thanh toán là số dư ví hay ngân hàng liên kết, vẫn có thể ghi khoản chi xác định được nhưng để nguồn tiền chưa xác định và không suy ra số dư ví.
- Đọc title, text, bigText và textLines qua các hằng `Notification.EXTRA_*`; chọn/ghép có kiểm soát để không lặp nội dung.
- Phân biệt ghi nợ/ghi có bằng cấu trúc của từng ngân hàng: dấu tiền, từ khóa và trạng thái giao dịch. Không dùng regex bắt con số đầu tiên.
- Tách riêng amount, currency, account đã che, thời điểm, mã giao dịch nếu có và balance. Parse không chắc chắn: chuyển mục cần kiểm tra, không tự kết luận là chi tiêu.
- Chuẩn hóa dấu phân cách theo mẫu của từng ngân hàng; test cả `50,000`, `50.000`, số dư bằng 0, và trường hợp nhiều con số trong cùng thông báo.
- Chỉ hiển thị số dư ngân hàng khi thông báo thực sự có số dư và xác định đúng tài khoản. Không có thì ghi `Thông báo không cung cấp số dư`.
- Với MoMo/ZaloPay, chỉ hiển thị số dư ví khi thông báo ghi rõ số dư ví. Không tự tính bằng số dư cũ trừ tiền thanh toán; ví có thể thanh toán từ ngân hàng liên kết. Không đọc nội dung màn hình ví bằng Accessibility.
- Dashboard hiển thị số dư gần nhất theo từng tài khoản, kèm thời điểm cập nhật. Không gộp nhầm số dư nhiều ngân hàng; không gọi số dư từ thông báo cũ là số dư hiện tại.
- Có thể ghi nhận giao dịch cộng tiền để cập nhật lịch sử/số dư nếu parser hỗ trợ, nhưng không mở popup hỏi lý do chi tiêu.

## 5. Dữ liệu và cấu trúc đề xuất

`Transaction`: id, bankPackage, bankName, accountKey nullable, amountMinor (giá trị dương), currency, direction, occurredAt nullable, receivedAt, balanceAfter nullable, reason nullable, categoryId nullable, status, dedupeKey, sourceNotificationKey và bankReference nullable. Lưu nội dung gốc chỉ khi cần kiểm tra parser; có cơ chế xóa và không ghi log ở bản release.

`Category`: id, name và thứ tự hiển thị. `Account`: id, bankPackage, định danh đã che; khi chưa phân biệt được tài khoản thì không cập nhật số dư chung một cách suy đoán. Settings lưu ngân hàng được bật và tùy chọn popup.

Khi triển khai, đổi các tên mang nghĩa riêng ngân hàng sang `providerPackage`, `providerName`, `providerReference` và `TransactionNotificationListener` để hỗ trợ cả ví và ngân hàng; thêm loại giao dịch `EXPENSE`, `INCOME`, `TRANSFER` và nguồn tiền nullable. Tổng chi chỉ cộng `EXPENSE`, không cộng chuyển nội bộ/nạp/rút ví đã xác định.

- `services/BankNotificationListener`: nhận callback hệ thống, lọc nguồn, giao xử lý bất đồng bộ; không làm việc DB nặng trên callback.
- `notifications/NotificationExtractor`, `parsers/BankParser`: chuyển notification thành dữ liệu parse có kiểu rõ ràng.
- `data/TransactionRepository`: chống trùng, lưu, cập nhật trạng thái và cung cấp Flow.
- `notifications/ExpenseReminderManager`: notification channel, nhắc bổ sung và điều hướng theo transactionId.
- `overlay/ExpenseOverlayController`: quản lý window, vòng đời, bàn phím, đóng popup và hàng đợi.
- `ui/`: onboarding, dashboard, lịch sử, pending, form và settings; ViewModel làm việc qua repository.

Chống trùng ưu tiên mã giao dịch ngân hàng + tài khoản khi có. Khi thiếu mã, dùng dấu vết kết hợp notification key, thời điểm sự kiện ổn định và nội dung chuẩn hóa. Notification key đơn lẻ không đủ vì ngân hàng có thể tái sử dụng cùng ID. Không gộp hai khoản chi thật chỉ vì cùng số tiền/nội dung. Dedupe được lưu bền vững bằng unique index; dữ liệu cập nhật cho cùng sự kiện phải giữ nguyên lý do/category đã nhập.

## 6. Chạy nền và popup

Android quản lý `NotificationListenerService`; dùng callback hệ thống, không polling liên tục. Theo dõi connected/disconnected, hủy coroutine đúng vòng đời, hiển thị tình trạng quyền/kết nối và dùng rebind khi phù hợp. Không mặc định thêm foreground service chạy vĩnh viễn hoặc hứa hoạt động sau force-stop.

Popup nền dùng `WindowManager` với `TYPE_APPLICATION_OVERLAY`, cần `SYSTEM_ALERT_WINDOW` được người dùng cấp. Chỉ một popup tại một thời điểm, có nút đóng, không che toàn màn hình; kiểm tra nhập liệu/bàn phím và dọn window khi service bị ngắt. Không yêu cầu mở khóa để nhập, không tự hiển thị dữ liệu giao dịch lên màn hình khóa. Một số màn hình có thể chặn overlay; giao dịch pending và thông báo là đường phục hồi.

Thông báo nhắc dùng channel riêng. Heads-up phụ thuộc thiết lập hệ thống/người dùng, không đảm bảo luôn bật. Không dùng full-screen intent cho lời nhắc chi tiêu. Khi quyền bị thu hồi hoặc thao tác hiện window thất bại, vẫn giữ dữ liệu để nhập sau.

## 7. Thứ tự triển khai và tiêu chí xong

| Bước | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- |
| 1. Nền tảng | Sửa Room/DAO/KSP; repository; form thêm tay tối thiểu | Build debug được; thêm/sửa giao dịch và mở lại app còn dữ liệu |
| 2. MoMo/ZaloPay | MoMo trước, ZaloPay sau khi có mẫu; viết extractor/parser và dedupe | Test chi/nhận/hoàn/nạp/chuyển nội bộ/thất bại/quảng cáo; đọc đúng amount/balance, không lưu trùng |
| 3. Luồng nhắc | Onboarding quyền; listener → DB → notification → form | Khoản trừ được lưu trước; bấm nhắc mở đúng giao dịch; Lưu/Để sau đúng trạng thái |
| 4. Popup tự hiện | Overlay, hàng đợi, xử lý lockscreen/quyền/vòng đời | Nhập lý do/category khi dùng app khác; nhiều thông báo không tạo nhiều window; không có quyền vẫn nhập sau được |
| 5. Màn hình chính | Lịch sử, pending, tổng chi theo danh mục, số dư theo tài khoản | Chi tính từ các khoản debit hợp lệ; pending được thể hiện rõ; số dư có nguồn/thời điểm |
| 6. Kiểm tra máy thật | Thông báo MoMo/ZaloPay thật, mất quyền, restart, OEM background limits | Hoàn tất luồng nhận → nhập → lưu → số dư nếu có; ghi lại giới hạn thực tế của máy/ví |

Bản test có nút **Mô phỏng giao dịch** cho các tình huống có số dư, không có số dư, thông báo lặp và nhiều giao dịch liên tiếp. Dữ liệu mô phỏng đi qua cùng pipeline nhưng đánh dấu test và tách khỏi thống kê thật; không bỏ bộ lọc package thật để nhận notification do app tự gửi. Việc mô phỏng không thay thế kiểm tra Notification Access bằng thông báo MoMo/ZaloPay thật.

Kiểm tra bắt buộc: thông báo cập nhật/lặp, hai khoản bằng nhau, DB sau restart, nhiều giao dịch liên tiếp, nội dung trống/ẩn, giao dịch không có số dư, số dư 0, nhiều tài khoản, từ chối quyền, màn hình khóa, ngân hàng thay đổi mẫu và thao tác Lưu nhiều lần. Tối thiểu một thiết bị trước Android 13 và một thiết bị Android 13+; kiểm tra riêng phiên bản Android mà app target và điện thoại dùng chính.

## 8. Dữ liệu và quyết định cần bổ sung

- Đã chốt MoMo hoặc ZaloPay cho bản đầu theo yêu cầu người dùng; mặc định MoMo trước, điều chỉnh theo ví có mẫu test.
- Mẫu thông báo thanh toán/chuyển tiền đi, nhận/hoàn/nạp tiền, có/không có số dư và giao dịch lỗi từ ví đang dùng; che dữ liệu cá nhân trước khi làm fixture.
- Thiết bị/phiên bản Android chính để kiểm tra popup và chạy nền.
- Bản đầu mặc định lưu local, không log thông báo thật; chủ động cấu hình backup cho dữ liệu giao dịch thay vì giữ rule mẫu mặc định.

## Tài liệu Android đối chiếu

- [MoMo trên Google Play](https://play.google.com/store/apps/details?id=com.mservice.momotransfer)
- [ZaloPay trên Google Play](https://play.google.com/store/apps/details?id=vn.com.vng.zalopay)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Giới hạn mở Activity từ nền](https://developer.android.com/guide/components/activities/secure-bal)
- [TYPE_APPLICATION_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Quyền gửi thông báo](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Room database và compiler](https://developer.android.com/training/data-storage/room)
