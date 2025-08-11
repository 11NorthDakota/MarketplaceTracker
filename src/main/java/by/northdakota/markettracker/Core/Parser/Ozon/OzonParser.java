package by.northdakota.markettracker.Core.Parser.Ozon;

import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OzonParser {

    private final OzonDataProvider dataProvider;

    public WebDriver getDataDriver(String productArticle) {
        return dataProvider.getProductDataDriver(productArticle);
    }

    public List<BigDecimal> getPriceList(WebDriver driver){
        List<BigDecimal> priceList = new ArrayList<>();

        WebElement priceContainer = driver.findElement(By.cssSelector("div[data-widget='webPrice']"));

        List<WebElement> priceSpans = priceContainer.findElements(By.xpath(".//span[contains(text(),'₽')]"));



        for (WebElement span : priceSpans) {
            String rawPrice = span.getText().replaceAll("[^\\d,\\.]", "").replace(',', '.');
            BigDecimal price = new BigDecimal(rawPrice);
            priceList.add(price);
        }
        return priceList;
    }

    public String getProductName(WebDriver driver){
        return driver.findElement(By.cssSelector("div[data-widget='webProductHeading'] h1")).getText();
    }

    public void closeDriver(){
        this.dataProvider.close();
    }

}
