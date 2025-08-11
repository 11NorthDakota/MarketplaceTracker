package by.northdakota.markettracker.Core.Parser.WB;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WbDataProvider {
    private final String url = "https://card.wb.ru/cards/v4/" +
            "detail?appType=1&curr=rub&dest=-1257786&spp=30&ab_testing=false&lang=ru&nm=";

    public JSONObject getProductData(String productArticle) throws IOException {
        String doc = Jsoup.connect(url+productArticle)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .get()
                .body()
                .text();
        return new JSONObject(doc);
    }
}
