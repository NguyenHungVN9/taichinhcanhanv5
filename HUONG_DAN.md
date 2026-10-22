# Theo dõi tài chính (Android) - miễn phí, không cần key

Đây là project Android đầy đủ (không cần tạo project mẫu).

## Cách 1: Build bằng Android Studio
1. File > Open > chọn thư mục TaiChinhApp, đợi Gradle sync (cần mạng lần đầu).
2. Build > Build Bundle(s)/APK(s) > Build APK(s).
3. File nằm ở app/build/outputs/apk/debug/app-debug.apk, chép sang điện thoại và cài.

## Cách 2: Build miễn phí trên GitHub, không cài gì (xem hướng dẫn trong chat)
Tạo repo, upload toàn bộ nội dung thư mục này (kể cả .github), vào tab Actions > Build APK > Run workflow,
đợi vài phút rồi tải file TaiChinh-APK ở phần Artifacts.

## Cài lên điện thoại
Chép app-debug.apk sang máy, mở file, cho phép "cài ứng dụng từ nguồn không xác định" khi được hỏi.
