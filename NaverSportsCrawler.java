import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

public class NaverSportsNewsCrawler {
    public static void main(String[] args) {
        // 환경변수 KEYWORD가 없으면 기본값 사용 (국내 야구 스포츠)
        String keyword = System.getenv("KEYWORD");
        if (keyword == null || keyword.isEmpty()) {
            keyword = "국내 야구 스포츠";
        }
        Monitoring monitoring = new Monitoring();
        monitoring.getNews(keyword, 10, 1, SortType.date);
    }
}

enum SortType {
    sim("sim"), date("date");

    final String value;

    SortType(String value) {
        this.value = value;
    }
}

class Monitoring {
    private final Logger logger;
    private final HttpClient client;

    public Monitoring() {
        logger = Logger.getLogger(Monitoring.class.getName());
        logger.setLevel(Level.INFO);
        client = HttpClient.newHttpClient();
    }

    // 뉴스와 이미지 데이터를 API를 통해 가져와 파일로 저장
    public void getNews(String keyword, int display, int start, SortType sort) {
        try {
            // 1. 뉴스 데이터 조회
            String newsResponse = getDataFromAPI("news.json", keyword, display, start, sort);
            JSONObject newsJson = new JSONObject(newsResponse);
            JSONArray items = newsJson.getJSONArray("items");

            // 뉴스 제목 저장 (HTML 태그 제거)
            String newsFileName = String.format("%d_%s_news.txt", new Date().getTime(), keyword);
            try (FileWriter writer = new FileWriter(new File(newsFileName))) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String title = item.getString("title").replaceAll("<.*?>", "");
                    writer.write(title + "\n");
                    logger.info("뉴스 제목: " + title);
                }
            }
            logger.info("뉴스 목록이 " + newsFileName + " 파일에 저장되었습니다.");

            // 2. 이미지 데이터 조회 (관련성 높은 결과: sort=sim)
            String imageResponse = getDataFromAPI("image", keyword, display, start, SortType.sim);
            JSONObject imageJson = new JSONObject(imageResponse);
            JSONArray imageItems = imageJson.getJSONArray("items");
            if (imageItems.length() > 0) {
                JSONObject firstImage = imageItems.getJSONObject(0);
                String imageLink = firstImage.getString("link").split("\\?")[0]; // 쿼리 파라미터 제거
                logger.info("이미지 링크: " + imageLink);

                HttpRequest imageRequest = HttpRequest.newBuilder()
                        .uri(URI.create(imageLink))
                        .build();
                String[] parts = imageLink.split("\\.");
                String ext = parts[parts.length - 1];
                String imageFileName = String.format("%d_%s_image.%s", new Date().getTime(), keyword, ext);
                Path imagePath = Path.of(imageFileName);
                client.send(imageRequest, HttpResponse.BodyHandlers.ofFile(imagePath));
                logger.info("이미지가 " + imageFileName + " 파일로 저장되었습니다.");
            } else {
                logger.warning("이미지 결과가 없습니다.");
            }
        } catch (Exception e) {
            logger.severe("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 네이버 API 호출 메서드
    private String getDataFromAPI(String path, String keyword, int display, int start, SortType sort) throws Exception {
        String url = "https://openapi.naver.com/v1/search/%s".formatted(path);
        String params = String.format("query=%s&display=%d&start=%d&sort=%s",
                URLEncoder.encode(keyword, "UTF-8"), display, start, sort.value);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .GET()
                .header("X-Naver-Client-Id", System.getenv("NAVER_CLIENT_ID"))
                .header("X-Naver-Client-Secret", System.getenv("NAVER_CLIENT_SECRET"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("API 응답 상태 코드: " + response.statusCode());
        if (response.statusCode() != 200) {
            throw new Exception("API 호출 실패: " + response.body());
        }
        return response.body();
    }
}
