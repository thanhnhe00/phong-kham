# Nghiên cứu nghiệp vụ phòng khám tư nhân (đầu vào cho thiết kế lại backend)

Cập nhật: 13/11/2026. Tài liệu gồm hai loại nội dung, được đánh dấu rõ:
- **[Luật]**: có căn cứ văn bản (nguồn ở cuối tài liệu).
- **[Thực tế]**: cách các cơ sở thường làm, lấy từ quy trình công bố của bệnh viện/phòng khám và từ phần mềm quản lý phòng khám. Đây không phải quy định bắt buộc, nhóm có thể chọn cách khác.

Những mục còn **(cần kiểm tra)** là chỗ chưa đọc được văn bản gốc. Trước khi chốt, nhóm cần mở trực tiếp phụ lục của văn bản để kiểm tra.

---

## 1. Khung pháp lý cần biết

| Văn bản | Nội dung liên quan đến hệ thống |
|---|---|
| Luật Khám bệnh, chữa bệnh 15/2023/QH15 | Người bệnh điều trị ngoại trú phải được lập và cập nhật hồ sơ bệnh án (Điều 69). Người bệnh có quyền được tóm tắt hoặc sao hồ sơ. |
| Thông tư 32/2023/TT-BYT (hiệu lực 01/01/2024) | Phụ lục XXVIII gồm 29 mẫu bệnh án, trong đó có **bệnh án ngoại trú chung** và các mẫu ngoại trú chuyên khoa (ví dụ Răng Hàm Mặt 16/BV1). Cơ sở được thêm trường ngoài mẫu. Phần hồ sơ bệnh án có thể đã được sửa đổi bởi thông tư sau **(cần kiểm tra)**. |
| Thông tư 13/2025/TT-BYT (hiệu lực 21/07/2025, thay Thông tư 46/2018) | Bệnh án điện tử (EMR). Bệnh viện phải xong trước 30/09/2025. Cơ sở khác (gồm phòng khám) hạn đến **31/12/2026** (cần kiểm tra Điều 4). Có 3 hình thức ký xác nhận, trong đó có chữ ký số. Đặc tả kỹ thuật ở hsba.kcb.vn. |
| Thông tư 26/2025/TT-BYT (hiệu lực 01/07/2025) | Đơn thuốc ngoại trú (mẫu ở Phụ lục I). Các yêu cầu chính được tóm ở mục 5. Bệnh viện phải kê đơn điện tử trước 01/10/2025, **cơ sở khác trước 01/01/2026** (theo công văn của Sở Y tế Hà Nội). |
| Hệ thống đơn thuốc quốc gia (donthuocquocgia.vn) | Phòng khám tư phải đăng ký **mã liên thông** cho cơ sở và cho từng người kê đơn, sau đó đẩy đơn điện tử lên hệ thống. |
| Thông tư 33/2025/TT-BYT | Thời hạn lưu trữ hồ sơ ngành y tế. Bệnh án ngoại trú thường được nêu là **10 năm** (nguồn thứ cấp, cần kiểm tra). |
| Nghị định 96/2023/NĐ-CP | Mỗi cơ sở phải có **một người chịu trách nhiệm chuyên môn kỹ thuật**: bác sĩ toàn thời gian, có chứng chỉ hành nghề, ít nhất 36 tháng hành nghề. Người hành nghề phải đăng ký với Sở Y tế thời gian làm việc tại cơ sở. |

**Hệ quả cho dự án:** kê đơn điện tử đã là bắt buộc, còn bệnh án điện tử sắp bắt buộc. Vì vậy hồ sơ khám và đơn thuốc phải được nhập trên phần mềm, không chỉ in giấy. Trong phạm vi đồ án, nhóm **mô phỏng** việc liên thông: lưu mã liên thông, mã đơn và trạng thái "đã gửi", không cần kết nối thật.

---

## 2. Các vai trò (actor)

| Vai trò | Có trong hệ thống hiện tại? | Việc chính |
|---|---|---|
| Bệnh nhân (app) | Có (`PATIENT`) | Đặt, đổi, hủy lịch; xem số thứ tự; xem kết quả, đơn thuốc, lịch sử khám |
| Trực ban / Tổng đài (CSKH) | **Chưa có** (đang gộp vào lễ tân) | Gọi xác nhận lịch hẹn, xử lý đổi/hủy, nhắc lịch |
| Lễ tân (tiếp đón) | Có (`RECEPTIONIST`) | Check-in, đối chiếu giấy tờ, tạo hồ sơ bệnh nhân vãng lai, cấp số |
| Thu ngân | **Chưa có** | Thu phí khám và phí cận lâm sàng, in biên lai/hóa đơn, hoàn tiền |
| Điều dưỡng | **Chưa có** | Đo sinh hiệu trước khi vào khám, hỗ trợ bác sĩ, in và phát giấy tờ |
| Bác sĩ | Có (`DOCTOR`) | Khám, chỉ định cận lâm sàng, chẩn đoán, kê đơn, hẹn tái khám |
| Kỹ thuật viên xét nghiệm / chẩn đoán hình ảnh | **Chưa có** (bác sĩ hoặc lễ tân nhập kết quả thay) | Thực hiện chỉ định, nhập kết quả, tải file kết quả |
| Dược sĩ (nhà thuốc trong phòng khám, nếu có) | **Chưa có** | Bán và cấp thuốc theo đơn |
| Quản lý / Admin | Có (`ADMIN`) | Danh mục, bảng giá, tài khoản, **xếp ca**, thống kê |

[Thực tế] Ở phòng khám nhỏ, một người thường kiêm nhiều vai trò: lễ tân kiêm thu ngân kiêm trực ban, điều dưỡng kiêm phát thuốc. Vì vậy nên thiết kế **quyền theo vai trò, và một tài khoản có thể có nhiều vai trò**, thay vì mỗi tài khoản chỉ một `role`.

---

## 3. Luồng đầu đến cuối

### 3.1 Đặt lịch online và trực ban xác nhận

1. Bệnh nhân chọn **chuyên khoa hoặc bác sĩ**, chọn ngày và khung giờ còn trống, nhập lý do khám. [Thực tế] Có hai kiểu: đặt theo bác sĩ cụ thể (phổ biến ở phòng khám tư) và đặt theo chuyên khoa rồi phòng khám tự phân bác sĩ đang trực.
2. Lịch được tạo ở trạng thái **CHỜ XÁC NHẬN**. Khung giờ được **giữ tạm** để người khác không đặt trùng.
3. Trực ban mở danh sách lịch chờ xác nhận, xếp theo giờ khám gần nhất, rồi **gọi điện** cho bệnh nhân. Cuộc gọi có thể dẫn đến:
   - **Xác nhận**: chuyển sang ĐÃ XÁC NHẬN, gửi tin hoặc thông báo cho bệnh nhân.
   - **Đổi giờ hoặc đổi bác sĩ**: trực ban chọn khung giờ mới, sau đó xác nhận.
   - **Bệnh nhân hủy**: chuyển sang ĐÃ HỦY và trả khung giờ.
   - **Không liên lạc được**: ghi lại lần gọi. Sau N lần gọi (ví dụ 3 lần) hoặc khi gần đến giờ hẹn thì tự hủy hoặc để trực ban quyết định.
   - Mỗi lần gọi được **ghi nhật ký**: ai gọi, lúc nào, kết quả, ghi chú. Nhật ký dùng để đối chiếu khi có khiếu nại.
4. Lịch đã xác nhận được **nhắc** trước 1 ngày và trước 1–2 giờ. Hệ thống hiện tại đã có `appointment-reminder.service.ts`.
5. Bệnh nhân được đổi hoặc hủy lịch trước giờ hẹn một khoảng quy định. Không đến khám nhiều lần thì bị khóa đặt online. Hệ thống hiện tại đã có quy tắc NO_SHOW.

> **Khác với hệ thống hiện tại:** hiện đặt lịch xong là tự `CONFIRMED` (`appointments.service.ts:117`). Chưa có hàng đợi xác nhận, nhật ký cuộc gọi và trạng thái "không liên lạc được".

### 3.2 Bệnh nhân đến phòng khám (tiếp đón)

1. **Check-in** tại quầy lễ tân bằng mã lịch hẹn, mã QR hoặc số điện thoại. Bệnh nhân vãng lai thì lấy số và tạo hồ sơ mới.
2. **Định danh bằng ít nhất 2 thông tin** (họ tên và ngày sinh), đối chiếu **CCCD hoặc mã định danh cá nhân**. [Luật] Đơn thuốc bắt buộc có số định danh, CCCD hoặc hộ chiếu (Thông tư 26/2025). Vì vậy cần thu thập ngay ở bước này, trừ trẻ em và trường hợp đặc biệt.
3. Cập nhật thông tin hành chính: địa chỉ cư trú, nghề nghiệp, dân tộc, người thân. Nếu là trẻ dưới 72 tháng tuổi thì ghi **tên người đưa trẻ đến khám**. Nếu có BHYT thì ghi thẻ BHYT.
4. **Thu phí khám**. [Thực tế] Phòng khám tư phổ biến nhất là **thu trước khi khám**. Lễ tân hoặc thu ngân thu tiền rồi in biên lai.
5. **Cấp số thứ tự** và phân vào **phòng khám hoặc bác sĩ**. Hệ thống hiện tại có số A cho bệnh nhân đặt lịch, số B cho bệnh nhân vãng lai, có luật gọi số. Phần này giữ lại được.

### 3.3 Điều dưỡng đo sinh hiệu (trước khi vào khám)

[Thực tế] Đây là bước phổ biến, ví dụ quy trình ngoại trú của Tâm Anh: bệnh nhân cầm số đến quầy điều dưỡng để đo mạch, nhiệt độ, huyết áp, nhịp thở, SpO2, cân nặng, chiều cao, và hỏi tiền sử dị ứng. Kết quả đo được ghi luôn vào hồ sơ của lượt khám, nhờ đó bác sĩ không phải tự đo.

> **Khác với hệ thống hiện tại:** sinh hiệu đang nằm trong `MedicalRecord`, do bác sĩ nhập. Nên cho điều dưỡng nhập trước, bác sĩ xem và sửa nếu cần.

### 3.4 Bác sĩ nhận ca

- Bác sĩ đăng nhập trên **máy tính tại phòng khám** (web). Màn hình hiển thị **hàng đợi của phòng hoặc ca mình đang trực**.
- Bác sĩ bấm **"Gọi"** để gọi người tiếp theo. Màn hình chờ và loa báo số, app báo cho bệnh nhân. Hệ thống hiện tại đã có phần này.
- Bác sĩ nhận ca theo **ca làm việc đã xếp** (xem mục 4). Mỗi ca có phòng, giờ bắt đầu và giờ kết thúc. Khung giờ đặt lịch online được **sinh ra từ ca làm việc của bác sĩ**, không nhập tay từng khung.
- Nếu bác sĩ nghỉ đột xuất, lễ tân hoặc quản lý **chuyển bệnh nhân** sang bác sĩ khác cùng chuyên khoa, và trực ban gọi báo cho những bệnh nhân đã đặt lịch.

### 3.5 Khám bệnh: bác sĩ điền phiếu khám trên web

[Thực tế] Bác sĩ điền ngay trên phần mềm (web hoặc máy tính). App bệnh nhân **chỉ để xem**. Thứ tự các mục theo bố cục mẫu bệnh án ngoại trú:

1. **Hỏi bệnh**: lý do khám, bệnh sử, tiền sử bản thân, tiền sử gia đình, dị ứng.
2. **Khám**: sinh hiệu (đã có từ điều dưỡng), khám toàn thân, khám các bộ phận.
3. **Chỉ định cận lâm sàng** (xét nghiệm, siêu âm, X-quang...) **chọn từ danh mục dịch vụ có giá**, không nhập chữ tự do. Sau đó:
   - Bệnh nhân **đóng tiền** cận lâm sàng ở thu ngân. Thu ngân thấy ngay khoản phải thu.
   - Bệnh nhân đi làm cận lâm sàng. Kỹ thuật viên nhập kết quả và tải file kết quả lên.
   - Lượt khám chuyển sang trạng thái **CHỜ KẾT QUẢ**. Khi đủ kết quả, bệnh nhân quay lại phòng và được gọi lại (hệ thống hiện tại đã có luật `RESULTS`).
4. **Chẩn đoán**: bệnh chính và bệnh kèm theo, kèm **mã ICD-10**. Hệ thống hiện tại đã có tra cứu ICD-10.
5. **Xử trí**: kê đơn, hẹn tái khám, chuyển viện, hoặc cấp giấy nghỉ ốm nếu có.
6. **Kê đơn điện tử** (yêu cầu ở mục 5). Bác sĩ chốt đơn, hệ thống cấp **mã đơn** và gửi lên Hệ thống đơn thuốc quốc gia (ở đồ án là mô phỏng). Muốn sửa đơn đã chốt thì **kê đơn mới thay đơn cũ**. Hệ thống hiện tại đã có `replacesId`, đúng với quy định.
7. **Ký xác nhận** hồ sơ và đơn: chữ ký số, hoặc in ra rồi ký tay. Sau khi ký thì **khóa sửa**. Muốn sửa phải tạo bản điều chỉnh và lưu lịch sử.

> Schema hiện tại (`MedicalRecord`) đã có hầu hết các mục trên: lý do khám, triệu chứng, tiền sử, sinh hiệu, khám toàn thân, khám bộ phận, tóm tắt cận lâm sàng, chẩn đoán kèm ICD, bệnh kèm theo, điều trị, ngày tái khám. Phần còn thiếu là **trạng thái ký hoặc khóa**, **người ký** và **lịch sử sửa**.

### 3.6 Kết thúc khám: ai in, ai giữ giấy tờ

| Giấy tờ | Ai tạo | Ai in và đưa cho bệnh nhân | Ai giữ bản gốc |
|---|---|---|---|
| Biên lai / hóa đơn | Thu ngân | Thu ngân | Phòng khám (kế toán) |
| Phiếu chỉ định cận lâm sàng | Bác sĩ | Điều dưỡng tại phòng khám | Hồ sơ |
| Phiếu kết quả cận lâm sàng | Kỹ thuật viên | Quầy trả kết quả hoặc điều dưỡng. Bệnh nhân cũng xem được trên app | Hồ sơ |
| **Đơn thuốc** | Bác sĩ | **Bác sĩ hoặc điều dưỡng in tại phòng khám, sau khi bác sĩ ký**. Bệnh nhân cầm đơn ra nhà thuốc. Bản điện tử có mã đơn để nhà thuốc tra | Bản điện tử lưu ở phòng khám và Hệ thống đơn thuốc quốc gia |
| **Hồ sơ bệnh án ngoại trú** | Bác sĩ | **Bệnh nhân không cầm bản gốc**. Phòng khám lưu bản gốc (tối thiểu khoảng 10 năm, cần kiểm tra). Khi bệnh nhân yêu cầu thì cấp **bản tóm tắt hoặc bản sao** | Phòng khám |
| Sổ khám bệnh / kết quả khám | Bác sĩ | [Thực tế] Nhiều phòng khám tư in "phiếu kết quả khám" một trang (chẩn đoán, dặn dò, hẹn tái khám) và đưa cho bệnh nhân cùng đơn thuốc | Bệnh nhân |
| Giấy hẹn tái khám, giấy nghỉ ốm, giấy chuyển viện | Bác sĩ | Điều dưỡng | Hồ sơ |

[Thực tế] Sau khi khám, nếu phòng khám có nhà thuốc thì bệnh nhân **mua thuốc theo đơn** rồi thanh toán. Nhà thuốc ghi nhận "đã bán theo đơn" bằng mã đơn.

> **Chưa xác minh:** một lượt khám thông thường (khám một lần, kê đơn) bắt buộc lập **bệnh án ngoại trú đầy đủ**, hay chỉ cần ghi sổ hoặc phiếu khám. Luật nói người bệnh **điều trị ngoại trú** phải có bệnh án. Thông tư 26 phân biệt hai trường hợp người bệnh **có** và **không có** hồ sơ bệnh án ngoại trú, tức là lượt khám đơn lẻ có thể không có bệnh án. Đề xuất cho đồ án: mỗi lượt khám luôn có một **phiếu khám** (MedicalRecord hiện tại); "bệnh án ngoại trú" là tập hợp các phiếu khám của một đợt điều trị (tùy chọn, làm sau).

### 3.7 Sau khám

- Hẹn tái khám: từ ngày tái khám, hệ thống tạo sẵn **lịch gợi ý** để bệnh nhân hoặc trực ban xác nhận.
- Kết quả cận lâm sàng về sau (gửi xét nghiệm ra ngoài): có hai lựa chọn, bệnh nhân quay lại hoặc xem online. Hệ thống hiện tại đã có `ResultMode RETURN | ONLINE`.
- Chăm sóc sau khám (gọi hỏi thăm) do trực ban làm, có thể dùng chung nhật ký cuộc gọi.

---

## 4. Ca làm việc của nhân viên

**[Luật]**
- Người hành nghề đăng ký **toàn thời gian hoặc một phần thời gian** với Sở Y tế, có ghi giờ làm việc tại cơ sở. Giờ làm phải theo Luật Lao động.
- Người chịu trách nhiệm chuyên môn phải làm toàn thời gian. Phòng khám chỉ được khám những chuyên khoa đang có người hành nghề **có mặt** trong giờ hoạt động (cần kiểm tra chi tiết trong Nghị định 96/2023).

**[Thực tế]**
- Ca thường gặp: **sáng 7h30–12h**, **chiều 13h30–17h**. Phòng khám tư thêm **ca tối (17h–20h)** và **thứ Bảy, Chủ nhật**, vì nhiều bác sĩ làm ở bệnh viện công và chỉ khám ngoài giờ.
- **Bác sĩ:** xếp lịch theo tuần hoặc tháng. Mỗi ca gắn **bác sĩ + phòng + chuyên khoa + giờ**. Mỗi ca có **số lượt khám tối đa**, và **tách hạn mức online và tại quầy** (ví dụ 70% cho đặt lịch, 30% cho vãng lai).
- **Lễ tân, thu ngân, điều dưỡng, trực ban:** xếp ca để có người trực quầy trong suốt giờ mở cửa. Ca không gắn với khung giờ khám của bệnh nhân.
- **Cách làm:** có **lịch mẫu lặp lại** (ví dụ BS A khám sáng thứ 2, 4, 6 tại phòng 3). Mỗi tuần sinh ca từ lịch mẫu, sau đó chỉnh các ngoại lệ: nghỉ phép, đổi ca, bác sĩ thay thế.
- Chấm công theo ca (check-in, check-out) dùng để tính lương. Đây là tùy chọn và ngoài phạm vi đồ án nếu không cần.

> **Khác với hệ thống hiện tại:** `Schedule` hiện là từng **khung giờ khám** của bác sĩ (`startTime`, `endTime`, `isBooked`). Bác sĩ **tự tạo** khung giờ, không ai duyệt (`POST /schedules/bulk`, chỉ role DOCTOR). Chưa có khái niệm **ca**, **phòng**, **lịch mẫu**, hay ca của nhân viên không phải bác sĩ.

### 4.1 Đã chốt: lịch cố định do quản lý xếp, đổi ca phải qua yêu cầu

Lý do: nếu để bác sĩ tự đăng ký thì sẽ có buổi thừa bác sĩ (ai cũng chọn buổi sáng), có buổi không có ai.

**Lịch cố định**
- Quản lý tạo **lịch mẫu theo tuần** cho từng bác sĩ, ví dụ "BS A, Nội, phòng 3: sáng thứ 2, 4, 6". Bác sĩ **không tự tạo ca**, chỉ xem được lịch của mình.
- Hệ thống **sinh ca** từ lịch mẫu cho một số tuần tới (ví dụ 4 tuần). Khung giờ đặt lịch sinh ra từ ca.
- Khi xếp lịch, quản lý thấy được **buổi nào của chuyên khoa nào đang thiếu bác sĩ**. Hệ thống chặn hai trường hợp: trùng phòng cùng giờ, và một bác sĩ bị xếp hai ca cùng lúc.
- Sửa lịch mẫu chỉ áp dụng cho các ca **chưa sinh** hoặc **chưa có lịch hẹn**. Ca đã có bệnh nhân đặt thì phải đổi theo luồng yêu cầu bên dưới.

**Yêu cầu đổi ca** (bác sĩ gửi, có lý do)
1. **Nhờ bác sĩ khác thay:** chỉ chọn được bác sĩ **cùng chuyên khoa** và **rảnh ca đó**. Bác sĩ được nhờ phải **đồng ý**, sau đó **quản lý duyệt**.
2. **Đổi chéo ca** với bác sĩ cùng chuyên khoa: hai bên đồng ý, sau đó quản lý duyệt.
3. **Xin nghỉ** (không có người thay): quản lý duyệt, rồi tự tìm người thay hoặc hủy ca.

**Khi yêu cầu được duyệt**
- Có người thay: lịch hẹn của ca **tự chuyển sang bác sĩ thay**, giữ nguyên giờ. Bệnh nhân nhận thông báo đổi bác sĩ. Trực ban gọi cho người muốn hủy.
- Không có người thay: ca bị hủy. Lịch hẹn chuyển về trạng thái **cần xử lý** để trực ban gọi cho từng bệnh nhân đổi giờ, đổi bác sĩ hoặc hủy.
- Mọi lần thay đổi đều **ghi nhật ký**: ai yêu cầu, ai đồng ý, ai duyệt, lúc nào.
- Yêu cầu phải gửi trước giờ ca một khoảng tối thiểu (ví dụ 24 giờ, cấu hình được). Trường hợp gấp do quản lý tự xử lý.

**DB cần thêm:** `Room`, `ShiftTemplate` (lịch mẫu: bác sĩ, thứ, buổi, phòng), `Shift` (ca cụ thể theo ngày, sinh từ lịch mẫu hoặc do quản lý tạo tay), `ShiftChangeRequest` (loại yêu cầu, người yêu cầu, người thay, trạng thái chờ đồng ý / chờ duyệt / đã duyệt / từ chối, lý do). `Schedule` (khung giờ) gắn với `Shift` thay vì gắn thẳng với bác sĩ.

---

## 5. Đơn thuốc theo Thông tư 26/2025: các yêu cầu ảnh hưởng đến DB

Nguồn: bài tóm tắt Thông tư (znews). **Phụ lục I gốc chưa đọc được, cần kiểm tra.**
- Thông tin người bệnh: **số định danh cá nhân, CCCD hoặc hộ chiếu**, nơi cư trú. Trẻ dưới 72 tháng tuổi phải có **số tháng tuổi, cân nặng, tên người đưa trẻ đến khám**.
- Mỗi thuốc ghi: tên thuốc theo **tên chung quốc tế (hoạt chất)**, hoặc hoạt chất kèm tên thương mại trong ngoặc; thuốc nhiều hoạt chất thì ghi tên thương mại. Ghi thêm **hàm lượng hoặc nồng độ**, số lượng, **liều mỗi lần, số lần mỗi ngày, đường dùng, thời điểm dùng**, số ngày dùng.
- Số lượng dưới 10 thì thêm số 0 phía trước. Thuốc gây nghiện ghi số lượng bằng số rồi bằng chữ. Thuốc độc ghi trước các thuốc khác.
- **Tối đa 30 ngày** mỗi thuốc. Có danh mục **252 bệnh** (đái tháo đường, tăng huyết áp...) được kê tối đa 90 ngày.
- Sửa đơn: **kê đơn mới thay đơn cũ**.
- Đơn điện tử: **mã đơn thuốc**, **mã liên thông của cơ sở và của người kê đơn**, người kê đơn ký số. Định dạng mã lấy theo hướng dẫn của Hệ thống đơn thuốc quốc gia (cần kiểm tra).

> **Khác với hệ thống hiện tại:** `PrescriptionItem` chưa có **đường dùng** và **thời điểm dùng**. `Medicine` chưa phân loại thuốc thường, gây nghiện, hướng thần, độc. Chưa có kiểm tra tối đa 30/90 ngày. `Doctor` chưa có **số chứng chỉ hành nghề** và **mã liên thông người kê đơn**. `Patient.nationalId` đang không bắt buộc.

---

## 6. Tổng hợp: những gì cần thêm hoặc sửa trong DB (đầu vào cho bước thiết kế)

| # | Thay đổi | Lý do |
|---|---|---|
| 1 | Lịch hẹn có trạng thái **chờ xác nhận**, kèm bảng **nhật ký cuộc gọi** (ai gọi, kết quả, ghi chú) | Luồng trực ban xác nhận lịch |
| 2 | Vai trò mới: **trực ban, thu ngân, điều dưỡng, kỹ thuật viên** (và dược sĩ nếu có nhà thuốc). Một tài khoản có thể có nhiều vai trò | Mục 2 |
| 3 | Bảng **phòng khám (Room)**, **ca làm việc (Shift)** và **lịch mẫu**. Khung giờ đặt lịch sinh ra từ ca. Mỗi ca có hạn mức online và tại quầy | Mục 4 |
| 4 | **Danh mục dịch vụ kèm bảng giá** (phí khám theo chuyên khoa hoặc bác sĩ, các dịch vụ cận lâm sàng). `LabOrder` trỏ tới dịch vụ thay vì nhập tên tự do | Thu tiền, thống kê |
| 5 | **Hóa đơn/phiếu thu (Invoice + InvoiceItem + Payment)**: thu phí khám, thu phí cận lâm sàng, hoàn tiền | Thu ngân. Hiện chưa có phần tiền |
| 6 | **Sinh hiệu tách khỏi phiếu khám** (hoặc ghi rõ ai nhập) để điều dưỡng nhập trước | Mục 3.3 |
| 7 | Phiếu khám và đơn thuốc có **trạng thái ký hoặc khóa**, **người ký, thời điểm ký**, **lịch sử sửa** | Thông tư 13/2025, Thông tư 26/2025 |
| 8 | Đơn thuốc: thêm **đường dùng, thời điểm dùng**, **phân loại thuốc**, kiểm tra 30/90 ngày, **mã liên thông** (mô phỏng) | Mục 5 |
| 9 | Bác sĩ: thêm **số chứng chỉ hành nghề**, **phạm vi chuyên môn**, **mã người kê đơn** | In trên đơn, liên thông |
| 10 | Bệnh nhân: CCCD bắt buộc khi kê đơn (trừ trẻ em). Lượt khám của trẻ dưới 72 tháng phải có **cân nặng và người đưa đến khám** | Thông tư 26/2025 |
| 11 | (Tùy chọn) Nhà thuốc: tồn kho, bán theo đơn | Chỉ làm nếu nhóm muốn mở rộng |

**Giữ nguyên được:** hàng đợi A/B và luật gọi số, QR check-in, ICD-10, `ResultMode`, đơn thay thế (`replacesId`), nhắc lịch, khóa khi không đến khám, liên kết tài khoản app với hồ sơ bệnh nhân.

---

## 7. Câu hỏi nhóm cần chốt trước khi thiết kế DB

1. Thu tiền **trước khi khám** (phổ biến) hay sau khi khám?
2. Bệnh nhân đặt lịch **theo bác sĩ**, **theo chuyên khoa**, hay cả hai?
3. Có làm **nhà thuốc hoặc kho thuốc** không, hay chỉ kê đơn?
4. Có **BHYT** không? Nếu có, phải xuất dữ liệu theo chuẩn XML của BHXH, khối lượng việc lớn hơn nhiều. Đề xuất bỏ khỏi phạm vi.
5. Ký số thật hay chỉ **mô phỏng** (lưu "đã ký" kèm người ký và thời điểm)? Đề xuất mô phỏng.
6. Có cần **chấm công hoặc tính lương** theo ca không? Đề xuất chỉ xếp ca, không chấm công.

---

## Nguồn

- Luật KBCB 2023, Điều 69: https://hethongphapluat.com/luat-kham-benh-chua-benh-2023/dieu-69
- Thông tư 32/2023/TT-BYT: https://thuvienphapluat.vn/van-ban/The-thao-Y-te/Thong-tu-32-2023-TT-BYT-huong-dan-Luat-Kham-benh-chua-benh-593360.aspx · https://caselaw.vn/van-ban-phap-luat/413094-thong-tu-so-32-2023-tt-byt-ngay-31-12-2023-cua-bo-truong-bo-y-te-huong-dan-luat-kham-benh-chua-benh
- 29 mẫu bệnh án theo Thông tư 32/2023 (có file .doc tải về): https://ketoananpha.vn/29-mau-ho-so-benh-an-theo-thong-tu-so-322023tt-byt
- Mẫu bệnh án ngoại trú Răng Hàm Mặt 16/BV1: https://thuvienphapluat.vn/hoi-dap-phap-luat/mau-benh-an-ngoai-tru-chuyen-khoa-rang-ham-mat-theothong-tu-322023ttbyt-138032730.html
- Thông tư 13/2025/TT-BYT (bệnh án điện tử): https://caselaw.vn/van-ban-phap-luat/587211-thong-tu-so-13-2025-tt-byt-ngay-06-06-2025-cua-bo-truong-bo-y-te-huong-dan-trien-khai-ho-so-benh-an-dien-tu · https://hoatieu.vn/phap-luat/thong-tu-13-2025-tt-byt-233052 · https://luatvietnam.vn/tin-van-ban-moi/3-hinh-thuc-ky-xac-nhan-trong-ho-so-benh-an-dien-tu-tu-21-7-2025-186-102457-article.html
- Đặc tả bệnh án điện tử của Bộ Y tế: https://hsba.kcb.vn/source-documents/reference-guides/GUIDE-HSBA-V2.0.docx
- Thông tư 26/2025/TT-BYT (đơn thuốc): https://luatvietnam.vn/y-te/thong-tu-26-2025-tt-byt-quy-dinh-ke-don-thuoc-hoa-duoc-sinh-pham-tai-co-so-kham-chua-benh-404246-d1.html · https://znews.vn/quy-dinh-ve-ke-don-thuoc-ap-dung-tu-17-nguoi-dan-can-biet-post1567976.html · https://vneconomy.vn/bo-y-te-quy-dinh-moi-ve-don-thuoc-va-ke-don-thuoc-trong-dieu-tri-ngoai-tru.htm
- Hạn kê đơn điện tử cho cơ sở ngoài bệnh viện, mã liên thông: https://thanhtra.com.vn/y-te-8CCF74D5E/cac-co-so-kham-chua-benh-phai-thuc-hien-ke-don-thuoc-dien-tu-truoc-ngay-112026-5ae86cdac.html · https://daibieunhandan.vn/tay-ninh-hau-kiem-viec-trien-khai-ke-don-thuoc-dien-tu-10422357.html · https://donthuocquocgia.vn/huong-dan-su-dung.pdf
- Thông tư 33/2025/TT-BYT (lưu trữ): https://hcc.nghean.gov.vn/laws/detail/Thong-tu-so-33-2025-TT-BYT-cua-Bo-Y-te-Quy-dinh-thoi-han-luu-tru-ho-so-tai-lieu-nganh-y-te-1882/ · https://ketoananpha.vn/quy-dinh-va-mau-ho-so-benh-an.html
- Người chịu trách nhiệm chuyên môn, giờ làm việc: https://ketoananpha.vn/dieu-kien-mo-phong-kham-tu-nhan.html · https://syt.bacgiang.gov.vn/documents/21459/19056281/1710128003878_Danh+sach+08-3.pdf/5c68e0f2-63b5-4306-9faf-641e712becab
- Quy trình tiếp đón và đo sinh hiệu: https://tamanhhospital.vn/danh-cho-khach-hang/quy-trinh-kham-benh-ngoai-tru/ · https://www.vinmec.com/huong-dan-khach-hang/quy-trinh-kham-chua-benh-tai-vinmec/
- Xác nhận và nhắc lịch (nguồn thương mại): https://www.brandsvietnam.com/congdong/topic/quy-trinh-xac-nhan-va-nhac-lich-chuan-6-buoc-giup-giam-ty-le-no-show-tai-phong-kham
- Phần mềm quản lý phòng khám (luồng thu ngân, chỉ định, hóa đơn điện tử): https://www.sapo.vn/blog/phan-mem-quan-ly-phong-kham · https://syt.bacgiang.gov.vn/documents/21459/14278115/1655111211544_C%C3%B4ng+v%C4%83n+%C4%91%E1%BB%81+ngh%E1%BB%8B+b%C3%A1o+gi%C3%A1+%28PM+QLBV+2022%29.pdf/e4642d28-dedd-4424-b248-d40023dac680
