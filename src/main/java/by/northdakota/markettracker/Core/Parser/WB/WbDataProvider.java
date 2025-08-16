package by.northdakota.markettracker.Core.Parser.WB;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.openqa.selenium.Proxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WbDataProvider {
    private final String url = "https://card.wb.ru/cards/v4/" +
            "detail?appType=1&curr=rub&dest=-1257786&spp=30&ab_testing=false&lang=ru&nm=";

    @Value("${selenium.proxyip}")
    private String proxyUrl;


    public JSONObject getProductData(String productArticle) throws IOException {
        String[] parts = proxyUrl.split(":");
        String proxyHost = parts[0];
        int proxyPort = Integer.parseInt(parts[1]);
        String doc = Jsoup.connect(url+productArticle)
                .ignoreContentType(true)
                .proxy(proxyHost, proxyPort)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 YaBrowser/25.6.0.0 Safari/537.36")
                .get()
                .body()
                .text();
        return new JSONObject(doc);
    }
}
