package by.northdakota.markettracker.Core.Service;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;
import by.northdakota.markettracker.Core.Entity.Marketplace;
import by.northdakota.markettracker.Core.Entity.Notification;
import by.northdakota.markettracker.Core.Entity.PriceHistory;
import by.northdakota.markettracker.Core.Entity.TrackedItem;
import by.northdakota.markettracker.Core.Parser.WB.WbDataProvider;
import by.northdakota.markettracker.Core.Parser.WB.WbParser;
import by.northdakota.markettracker.Core.Repository.PriceHistoryRepository;
import by.northdakota.markettracker.Core.Repository.TrackedItemRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
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

@Service
@RequiredArgsConstructor
public class WbTrackerService implements TrackerService{

    private static final Logger logger = LoggerFactory.getLogger(WbTrackerService.class);

    private final WbDataProvider wbDataProvider;
    private final WbParser wbParser;
    private final TrackedItemRepository trackedItemRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<TrackedItemDto> startTracking(String article, Long chatId) throws IOException {

        if(trackedItemRepository.existsByArticleAndChatIdAndMarketplace(article, chatId,Marketplace.WB)) {
            eventPublisher.publishEvent(new Notification(chatId,"Товар уже отслеживается!"));
            return Optional.empty();
        }

        JSONObject object = wbDataProvider.getProductData(article);
        JSONArray products = object.getJSONArray("products");
        if (products == null || products.toList().isEmpty()) {
            eventPublisher.publishEvent(new Notification(chatId, "Товар с таким артикулом не найден!"));
            return Optional.empty();
        }
        Map<String, Object> priceList = wbParser.getPriceList(object);
        BigDecimal currentPrice = new BigDecimal(priceList.get("product").toString());
        BigDecimal basicPrice = new BigDecimal(priceList.get("basic").toString());

        String productName = wbParser.getProductName(object);

        TrackedItem trackedItem = TrackedItem.builder()
                .currentPrice(currentPrice)
                .basicPrice(basicPrice)
                .salePrice(null)
                .title(productName)
                .marketplace(Marketplace.WB)
                .article(article)
                .chatId(chatId)
                .priceHistory(new ArrayList<>())
                .build();

        PriceHistory history = new PriceHistory();
        history.setPrice(currentPrice);
        history.setItem(trackedItem);
        history.setTimestamp(LocalDateTime.now());
        List<PriceHistory> priceHistory =  trackedItem.getPriceHistory();
        priceHistory.add(history);
        trackedItem.setPriceHistory(priceHistory);
        trackedItem = trackedItemRepository.save(trackedItem);

        priceHistoryRepository.save(history);


        TrackedItemDto dto = new TrackedItemDto(
                article,
                productName,
                currentPrice,
                basicPrice,
                null,
                Marketplace.WB

        );
        logger.info("Tracked Item: {}", trackedItem);
        logger.info("Price History : {}", history);
        return Optional.of(dto);
    }

    @Transactional
    public void stopTracking(String article,Long chatId) {
        trackedItemRepository.deleteByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.WB);
        logger.info("Товар с артикулом {} и chatId {} больше не отслеживается", article,chatId);
    }

    public List<TrackedItemDto> getUserTrackedItem(Long chatId) {
        Optional<List<TrackedItem>> trackedItemsOpt = trackedItemRepository
                .findAllByChatIdAndMarketplace(chatId,Marketplace.WB);
        if(trackedItemsOpt.isEmpty()){
            eventPublisher.publishEvent(new Notification(chatId,"Товары на WB не отслеживаются!"));
            return Collections.emptyList();
        }
        List<TrackedItem> trackedItems = trackedItemsOpt.get();
        List<TrackedItemDto> trackedItemsDto = new ArrayList<>();
        for(TrackedItem item : trackedItems) {
            TrackedItemDto dto = new TrackedItemDto();
            dto.setArticle(item.getArticle());
            dto.setTitle(item.getTitle());
            dto.setBasicPrice(item.getBasicPrice());
            dto.setCurrentPrice(item.getCurrentPrice());
            dto.setMarketplace(item.getMarketplace());
            dto.setSalePrice(item.getSalePrice());
            trackedItemsDto.add(dto);
        }
        return trackedItemsDto;
    }

    @Scheduled(fixedDelayString = "PT30M",initialDelayString = "PT30M")
    @Async
    public void checkPrice() throws IOException {
        Optional<List<TrackedItem>> itemListOpt = trackedItemRepository.findAllByMarketplace(Marketplace.WB);

        if(itemListOpt.isEmpty()){
            logger.info("Товары на WB не отслеживаются!");
            logger.info("Проверка цен завершена");
            return;
        }

        List<TrackedItem> itemList = itemListOpt.get();

        for(TrackedItem item : itemList){
            JSONObject data = wbDataProvider.getProductData(item.getArticle());
            Map<String,Object> priceList = wbParser.getPriceList(data);
            BigDecimal newPrice = new BigDecimal(priceList.get("product").toString());
            if(item.getCurrentPrice().compareTo(newPrice) != 0){
                BigDecimal oldPrice = item.getCurrentPrice();
                item.setCurrentPrice(newPrice);
                trackedItemRepository.save(item);

                logger.info("Tracked Item info updated: {}", item);

                PriceHistory history = new PriceHistory();
                history.setItem(item);
                history.setPrice(newPrice);
                history.setTimestamp(LocalDateTime.now());
                priceHistoryRepository.save(history);

                logger.info("Price History saved: {}", history);

                String message = String.format("У товара %s (арт. %s) с маркетплейса %s\n💰 Изменилась цена: %s → %s",
                        item.getTitle(),
                        Marketplace.WB,
                        item.getArticle(),
                        oldPrice.divide(BigDecimal.valueOf(100)).doubleValue() ,
                        newPrice.divide(BigDecimal.valueOf(100)).doubleValue());

                eventPublisher.publishEvent(new Notification(item.getChatId(),message));
                logger.info("Обновлена цена и отправлено уведомление : /{}/ в чат пользователя {}",
                        message,item.getChatId());

            }

        }
        logger.info("Проверка цен завершена");
    }



}
