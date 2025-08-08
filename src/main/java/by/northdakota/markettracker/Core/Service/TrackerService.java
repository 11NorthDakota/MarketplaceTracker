package by.northdakota.markettracker.Core.Service;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;
import by.northdakota.markettracker.Core.Entity.Notification;
import by.northdakota.markettracker.Core.Entity.PriceHistory;
import by.northdakota.markettracker.Core.Entity.TrackedItem;
import by.northdakota.markettracker.Core.Parser.DataProvider;
import by.northdakota.markettracker.Core.Parser.WbParser;
import by.northdakota.markettracker.Core.Repository.PriceHistoryRepository;
import by.northdakota.markettracker.Core.Repository.TrackedItemRepository;
import by.northdakota.markettracker.TelegramBot.TrackerTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackerService {

    private static final Logger logger = LoggerFactory.getLogger(TrackerService.class);

    private final DataProvider dataProvider;
    private final WbParser wbParser;
    private final TrackedItemRepository trackedItemRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<TrackedItemDto> startTracking(String article, Long chatId) throws IOException {

        if(trackedItemRepository.existsByArticleAndChatId(article, chatId)) {
            eventPublisher.publishEvent(new Notification(chatId,"Товар уже отслеживается!"));
            return Optional.empty();
        }

        JSONObject object = dataProvider.getProductData(article);
        JSONArray products = object.getJSONArray("products");
        if (products == null || products.toList().isEmpty()) {
            eventPublisher.publishEvent(new Notification(chatId, "Товар с таким артиклем не найден!"));
            return Optional.empty();
        }
        Map<String, Object> priceList = wbParser.getPriceList(object);
        BigDecimal currentPrice = new BigDecimal(priceList.get("product").toString());
        BigDecimal basicPrice = new BigDecimal(priceList.get("basic").toString());

        String productName = wbParser.getProductName(object);

        TrackedItem trackedItem = TrackedItem.builder()
                .currentPrice(currentPrice)
                .basicPrice(basicPrice)
                .title(productName)
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
                basicPrice);
        logger.info("Tracked Item: {}", trackedItem);
        logger.info("Price History : {}", history);
        return Optional.of(dto);
    }

    @Transactional
    public void stopTracking(String article,Long chatId) throws IOException {
        trackedItemRepository.deleteByArticleAndChatId(article,chatId);
        logger.info("Item with article {} and chatId {} is no longer tracked", article,chatId);
    }

    public List<TrackedItemDto> getUserTrackedItem(Long chatId) {
        List<TrackedItem> trackedItems = trackedItemRepository.findAllByChatId(chatId);
        List<TrackedItemDto> trackedItemDtos = new ArrayList<>();
        for(TrackedItem item : trackedItems) {
            TrackedItemDto dto = new TrackedItemDto();
            dto.setArticle(item.getArticle());
            dto.setTitle(item.getTitle());
            dto.setBasicPrice(item.getBasicPrice());
            dto.setCurrentPrice(item.getCurrentPrice());
            trackedItemDtos.add(dto);
        }
        return trackedItemDtos;
    }

    @Scheduled(fixedDelayString = "PT30M",initialDelayString = "PT30M")
    public void checkPrice() throws IOException {
        List<TrackedItem> itemList = trackedItemRepository.findAll();

        for(TrackedItem item : itemList){
            JSONObject data = dataProvider.getProductData(item.getArticle());
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

                String message = String.format("У товара %s (арт. %s)\n💰 Изменилась цена: %s → %s",
                item.getTitle(), item.getArticle(),
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
