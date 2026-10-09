# MB Bank, TPBank và Vietcombank

Nghiên cứu ngày 09/10/2026. Nhận diện đang ở mức thử nghiệm; chưa kiểm tra thông báo push thật trên điện thoại. Các fixture trong `BankParserTest` và `NotificationSamples` là dữ liệu tổng hợp để kiểm tra ngữ pháp, không phải mẫu thông báo được ngân hàng xác nhận.

## Nguồn chính thức

Package Android được đối chiếu với trang ứng dụng do ngân hàng phát hành:

| Ngân hàng | Package | Nguồn |
| --- | --- | --- |
| MB Bank | `com.mbmobile` | [Google Play](https://play.google.com/store/apps/details?id=com.mbmobile) |
| TPBank | `com.tpb.mb.gprsandroid` | [Google Play](https://play.google.com/store/apps/details?id=com.tpb.mb.gprsandroid) |
| Vietcombank | `com.VCB` | [Google Play](https://play.google.com/store/apps/details?id=com.VCB) |

- [MB: thông báo biến động số dư trên App MBBank](https://news.mbbank.com.vn/news/bao-ve-tai-khoan-va-the-len-toi-100-trieu-djong-cho-khach-hang-mb-tu-0411-1638497813).
- [TPBank: thông báo trên App TPBank](https://tpb.vn/tin-tuc/tin-tpbank/tpbank-dieu-chinh-chinh-sach-dich-vu-thong-bao-bien-dong-so-du-sms-banking).
- [Vietcombank: hướng dẫn cài đặt OTT Alert](https://www.vietcombank.com.vn/-/media/Project/VCB-Sites/VCB/KHCN/Lien-he-va-Ho-tro/Huong-dan-su-dung/file/VCB-Digibank/VCB-Digibank-cai-dat-quan-trong/5-Cai-at-quan-ly-thong-bao-so-du-OTT-Alertpdf.pdf?ts=20230516032942).

Các nguồn xác nhận ứng dụng và kênh thông báo, không xác nhận mọi câu chữ push hay `Notification.extras` hiện hành. Không suy diễn mẫu SMS thành mẫu push đã kiểm chứng.

## Quy tắc đã triển khai

Listener dùng allowlist package trên; bật/tắt từng nguồn trong Thiết lập. Parser ngân hàng yêu cầu nhãn tài khoản (`TK`/`Tài khoản`/`Account`) và một khoản trừ VND rõ ràng: dấu `-` sau nhãn giao dịch/tài khoản, hoặc `giảm`/`ghi nợ`/`trích nợ`. Tiền vào không tạo khoản chi. Không đoán amount từ số dư, số tài khoản hay phí.

Ví dụ tổng hợp được nhận:

```text
TK 000000000001 GD: -50,000VND; SD: 1,200,000VND
Tài khoản 000000000002 giảm 50.000 VND. Số dư khả dụng: 0 VND
TK 000000000003; Số tiền GD: -50,000 VND; Số dư: 1,200,000 VND
```

Chỉ nhận số tiền nguyên VND, dấu nhóm nghìn `.` hoặc `,`, không trộn dấu; không hỗ trợ số thập phân/ngoại tệ. Số dư thiếu hoặc nhiều nhãn số dư sẽ để trống. Phần sau `ND:`/`Nội dung:`/`Diễn giải:` không được dùng để lấy amount/balance. OTP, lỗi, đang xử lý, quảng cáo và nhiều biến động mâu thuẫn bị bỏ qua. Rút tiền, nạp ví, tiết kiệm, hoàn tiền và chuyển nội bộ có nhãn rõ cũng bị bỏ qua.

Chuyển nội bộ không có nhãn rõ vẫn có thể bị tính vào chi; chưa phân biệt tài khoản của cùng chủ. Số dư hiển thị là số dư từ thông báo gần nhất có dữ liệu theo nhà cung cấp, không phải tổng nhiều tài khoản hay số dư truy vấn trực tiếp. Chống trùng vẫn dùng notification key/thời điểm/amount; chưa có mã tham chiếu ngân hàng đã kiểm chứng. Thanh toán qua ví và ngân hàng liên kết có thể tạo hai thông báo cho cùng khoản chi; có thể tắt một nguồn theo dõi để tránh cộng hai lần.

## Kiểm tra

21 test parser đã chạy độc lập bằng Kotlin/JUnit trong cache, gồm 6 test ví cũ và 15 test mới. Demo cả 5 nguồn đi qua parser trước khi lưu và tách khỏi dữ liệu thật. Demo không kiểm tra quyền Notification Access.

Build APK/Gradle chưa hoàn tất: Gradle wrapper cần tải distribution nhưng kết nối mạng của shell bị chặn. Chưa nghiệm thu Compose/KSP, popup hay listener trên thiết bị.

Trên máy thật: bật thông báo biến động số dư trong ứng dụng ngân hàng và quyền đọc thông báo của AutoCountMoney; đối chiếu một giao dịch trừ tiền, một giao dịch cộng tiền, cập nhật/lặp notification, từ chối quyền và màn hình khóa. Nếu mẫu không được nhận, cần fixture đã che thông tin cá nhân rồi bổ sung quy tắc/test; không mở rộng bằng cách lấy số tiền bất kỳ trong thông báo.
