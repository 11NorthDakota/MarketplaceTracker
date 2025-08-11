package by.northdakota.markettracker.Core.Parser.Ozon;


import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OzonDataProvider implements Closeable {

    private WebDriver driver;

    private final Logger logger = LoggerFactory.getLogger(OzonDataProvider.class);

    @Value("${selenium.url}")
    private String url;

    @Value("${selenium.proxyip}")
    private String proxyip;

    public WebDriver getProductDataDriver(String productArt) {
        try {
            if(driver == null){
                ChromeOptions options = new ChromeOptions();
                Proxy proxy = new Proxy();
                proxy.setHttpProxy(proxyip);
                options.setProxy(proxy);
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.addArguments("--window-size=1920,1080");
                options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
                options.setExperimentalOption("useAutomationExtension", false);
                options.addArguments("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 YaBrowser/25.6.0.0 Safari/537.36");

                URL seleniumServerUrl = new URL(url);

                driver = new RemoteWebDriver(seleniumServerUrl, options);
            }

            driver.get("https://www.ozon.ru/product/" + productArt);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-widget='webProductHeading'] h1")));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-widget='webPrice']")));

        }catch (MalformedURLException exception) {
            logger.info("MalformedURLException");
        }
        catch (TimeoutException exception) {
            logger.info("Timeoutexception");
        }
        return driver;
   }

   public void close(){
        if(driver != null){
            driver.quit();
            driver = null;
        }
   }

}
