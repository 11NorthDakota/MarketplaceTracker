package by.northdakota.markettracker.Core.Service;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;
import by.northdakota.markettracker.Core.Entity.Marketplace;
import by.northdakota.markettracker.Core.Entity.Notification;
import by.northdakota.markettracker.Core.Entity.PriceHistory;
import by.northdakota.markettracker.Core.Entity.TrackedItem;
import by.northdakota.markettracker.Core.Parser.Ozon.OzonParser;
import by.northdakota.markettracker.Core.Repository.PriceHistoryRepository;
import by.northdakota.markettracker.Core.Repository.TrackedItemRepository;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
@Service
public class OzonTrackerService implements TrackerService {

    private final Logger loggerOzon = LoggerFactory.getLogger(OzonTrackerService.class);
    private final OzonParser ozonParser;
    private final TrackedItemRepository trackedItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PriceHistoryRepository priceHistoryRepository;

    @Override
    @Transactional
    public Optional<TrackedItemDto> startTracking(String article, Long chatId){

        if(trackedItemRepository.existsByArticleAndChatIdAndMarketplace(article, chatId, Marketplace.OZON)) {
            eventPublisher.publishEvent(new Notification(chatId,"Товар уже отслеживается!"));
            return Optional.empty();
        }

        WebDriver driver = ozonParser.getDataDriver(article);

        List<BigDecimal> priceList =  ozonParser.getPriceList(driver);
        String productName = ozonParser.getProductName(driver);
        ozonParser.closeDriver();

        if(productName == null || productName.isEmpty()) {
            eventPublisher.publishEvent(new Notification(chatId,"Товар с таким артикулом не найден!"));
            return Optional.empty();
        }

        TrackedItem item = TrackedItem.builder()
                .salePrice(priceList.get(0))
                .currentPrice(priceList.get(1))
                .basicPrice(priceList.get(2))
                .marketplace(Marketplace.OZON)
                .priceHistory(new ArrayList<>())
                .chatId(chatId)
                .article(article)
                .title(productName)
                .build();

        PriceHistory history = new PriceHistory();
        history.setPrice(priceList.get(1));
        history.setItem(item);
        history.setTimestamp(LocalDateTime.now());
        List<PriceHistory> priceHistory =  item.getPriceHistory();
        priceHistory.add(history);
        item.setPriceHistory(priceHistory);
        item = trackedItemRepository.save(item);

        priceHistoryRepository.save(history);

        TrackedItemDto dto = new TrackedItemDto(
                article,
                productName,
                priceList.get(1),
                priceList.get(2),
                priceList.get(0),
                Marketplace.OZON

        );

        loggerOzon.info("Tracked Item: {}", item);
        loggerOzon.info("Price History : {}", history);
        return Optional.of(dto);
    }

    @Override
    @Transactional
    public void stopTracking(String article, Long chatId) throws IOException {
        trackedItemRepository.deleteByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.OZON);
        loggerOzon.info("Товар с артикулом {} и chatId {} больше не отслеживается", article,chatId);
    }

    @Override
    public List<TrackedItemDto> getUserTrackedItem(Long chatId) {
        Optional<List<TrackedItem>> trackedItemsOpt = trackedItemRepository
                .findAllByChatIdAndMarketplace(chatId,Marketplace.OZON);
        if(trackedItemsOpt.isEmpty()){
            eventPublisher.publishEvent(new Notification(chatId,"Товары на OZON не отслеживаются!"));
            return Collections.emptyList();
        }
        List<TrackedItem> trackedItems = trackedItemsOpt.get();
        List<TrackedItemDto> trackedItemDtos = new ArrayList<>();
        for(TrackedItem item : trackedItems) {
            TrackedItemDto dto = new TrackedItemDto();
            dto.setArticle(item.getArticle());
            dto.setTitle(item.getTitle());
            dto.setBasicPrice(item.getBasicPrice());
            dto.setCurrentPrice(item.getCurrentPrice());
            dto.setMarketplace(item.getMarketplace());
            dto.setSalePrice(item.getSalePrice());
            trackedItemDtos.add(dto);
        }
        return trackedItemDtos;
    }

    @Override
    @Scheduled(fixedDelayString = "PT30M",initialDelayString = "PT40M")
    @Async
    public void checkPrice() throws IOException {
        Optional<List<TrackedItem>> itemListOpt = trackedItemRepository.findAllByMarketplace(Marketplace.OZON);

        if(itemListOpt.isEmpty()){
            loggerOzon.info("Товары на OZON не отслеживаются!");
            loggerOzon.info("Проверка цен завершена");
            return;
        }

        List<TrackedItem> itemList = itemListOpt.get();

        for(TrackedItem item : itemList){
            WebDriver driver = ozonParser.getDataDriver(item.getArticle());
            List<BigDecimal> priceList =  ozonParser.getPriceList(driver);
            String productName = ozonParser.getProductName(driver);
            ozonParser.closeDriver();
            BigDecimal newPrice = priceList.get(1);
            BigDecimal salePrice = priceList.get(0);
            if(item.getCurrentPrice().compareTo(newPrice) != 0){
                BigDecimal oldPrice = item.getCurrentPrice();
                item.setSalePrice(salePrice);
                item.setCurrentPrice(newPrice);
                trackedItemRepository.save(item);

                loggerOzon.info("Tracked Item info updated: {}", item);

                PriceHistory history = new PriceHistory();
                history.setItem(item);
                history.setPrice(newPrice);
                history.setTimestamp(LocalDateTime.now());
                priceHistoryRepository.save(history);

                loggerOzon.info("Price History saved: {}", history);

                String message = String.format("У товара %s (арт. %s) с маркетплейса %s\n💰 Изменилась цена: %s → %s",
                        item.getTitle(),
                        Marketplace.OZON,
                        item.getArticle(),
                        oldPrice,
                        newPrice);

                eventPublisher.publishEvent(new Notification(item.getChatId(),message));
                loggerOzon.info("Обновлена цена и отправлено уведомление : /{}/ в чат пользователя {}",
                        message,item.getChatId());

            }

        }
        loggerOzon.info("Проверка цен завершена");
    }
}
