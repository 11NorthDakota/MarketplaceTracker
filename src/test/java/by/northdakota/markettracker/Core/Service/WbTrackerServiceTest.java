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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class WbTrackerServiceTest {

    @Mock
    private TrackedItemRepository trackedItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private WbDataProvider wbDataProvider;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;
    @Mock
    private WbParser wbParser;

    @InjectMocks
    private WbTrackerService wbTrackerService;

    private final String article = "14331433";
    private final Long chatId = 1433L;
    private final String productName = "T-Shirt";

    @Test
    void startTracking_shouldReturnEmpty_whenItemAlreadyTracked() throws IOException {
        Mockito.when(trackedItemRepository.existsByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.WB))
                .thenReturn(true);

        var result = wbTrackerService.startTracking(article,chatId);

        assertTrue(result.isEmpty());
        verify(eventPublisher).publishEvent(any(Notification.class));
        verifyNoInteractions(wbDataProvider,wbParser,priceHistoryRepository);
    }

    @Test
    void startTracking_shouldSaveAndReturnItemDto_whenProductFound() throws IOException{
        Mockito.when(trackedItemRepository.existsByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.WB))
                .thenReturn(false);

        JSONObject object = new JSONObject().put("products",new JSONArray().put(new JSONObject()));

        Mockito.when(wbDataProvider.getProductData(article)).thenReturn(object);
        Mockito.when(wbParser.getPriceList(object)).thenReturn(Map.of(
                "product", BigDecimal.valueOf(100),
                "basic",BigDecimal.valueOf(140)));
        Mockito.when(wbParser.getProductName(object)).thenReturn(productName);

        TrackedItem trackedItem = TrackedItem.builder()
                .currentPrice(BigDecimal.valueOf(100))
                .basicPrice(BigDecimal.valueOf(140))
                .salePrice(null)
                .title(productName)
                .marketplace(Marketplace.WB)
                .article(article)
                .chatId(chatId)
                .priceHistory(new ArrayList<>())
                .build();

        Mockito.when(trackedItemRepository.save(any(TrackedItem.class))).thenReturn(trackedItem);

        Optional<TrackedItemDto> result = wbTrackerService.startTracking(article,chatId);

        assertTrue(result.isPresent());

        assertEquals(productName,result.get().getTitle());
        verify(trackedItemRepository).save(any(TrackedItem.class));
        verify(priceHistoryRepository).save(any(PriceHistory.class));
    }

    @Test
    void startTracking_shouldReturnEmpty_whenProductNotFound()throws IOException{

        Mockito.when(trackedItemRepository.existsByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.WB))
                .thenReturn(false);

        JSONObject object = new JSONObject().put("products",new JSONArray());

        Mockito.when(wbDataProvider.getProductData(article)).thenReturn(object);

        var result = wbTrackerService.startTracking(article,chatId);

        assertTrue(result.isEmpty());

        verify(eventPublisher).publishEvent(any(Notification.class));
        verifyNoInteractions(priceHistoryRepository);
    }

    @Test
    void stopTracking_shouldDeleteItem() throws IOException {
        wbTrackerService.stopTracking(article,chatId);
        verify(trackedItemRepository).deleteByArticleAndChatIdAndMarketplace(article,chatId,Marketplace.WB);
    }

    @Test
    void getUserTrackedItem_shouldReturnTrackedItems_whenItemsAlreadyTracked(){
        List<TrackedItem> trackedItems = createTrackedItems();
        Mockito.when(trackedItemRepository.findAllByChatIdAndMarketplace(chatId,Marketplace.WB))
                .thenReturn(Optional.of(trackedItems));

        var result = wbTrackerService.getUserTrackedItem(chatId);

        assertAll(
                ()->assertFalse(result.isEmpty()),
                ()->assertEquals(3,result.size()),
                ()->assertEquals(article+"1",result.get(0).getArticle()),
                ()->assertEquals(article+"2",result.get(1).getArticle()),
                ()->assertEquals(article+"3",result.get(2).getArticle())
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getUserTrackedItem_shouldReturnEmptyList_whenNoTrackedItemFound(){
        Mockito.when(trackedItemRepository.findAllByChatIdAndMarketplace(chatId,Marketplace.WB))
                .thenReturn(Optional.empty());

        var result = wbTrackerService.getUserTrackedItem(chatId);
        assertTrue(result.isEmpty());
        verify(eventPublisher,times(1)).publishEvent(any(Notification.class));
    }

    @Test
    void checkPrice_shouldChangePrice_whenActualPriceChanges() throws IOException {

        List<TrackedItem> trackedItems = createTrackedItems();

        Mockito.when(trackedItemRepository.findAllByMarketplace(Marketplace.WB))
                .thenReturn(Optional.of(trackedItems));


        JSONObject data = new JSONObject()
                .put("products",new JSONArray());

        for(TrackedItem trackedItem : trackedItems){
            Mockito.when(wbDataProvider.getProductData(trackedItem.getArticle())).thenReturn(data);
            Mockito.when(wbParser.getPriceList(data)).thenReturn(Map.of("product",new BigDecimal(100)));
        }


        wbTrackerService.checkPrice();

        int times = trackedItems.size();

        verify(trackedItemRepository,times(times)).save(any(TrackedItem.class));
        verify(priceHistoryRepository,times(times)).save(any(PriceHistory.class));
        verify(eventPublisher,times(times)).publishEvent(any(Notification.class));
    }

    @Test
    void checkPrice_shouldDoNothing_whenPriceDidntChange() throws IOException {
        List<TrackedItem> trackedItems = createTrackedItems();

        Mockito.when(trackedItemRepository.findAllByMarketplace(Marketplace.WB))
                .thenReturn(Optional.of(trackedItems));

        for (TrackedItem item : trackedItems) {
            JSONObject data = new JSONObject(); // можешь оставить пустой
            Mockito.when(wbDataProvider.getProductData(item.getArticle()))
                    .thenReturn(data);
            Mockito.when(wbParser.getPriceList(data))
                    .thenReturn(Map.of("product", item.getCurrentPrice()));
        }

        wbTrackerService.checkPrice();

        verify(trackedItemRepository).findAllByMarketplace(Marketplace.WB);
        verifyNoMoreInteractions(trackedItemRepository);
        verifyNoInteractions(priceHistoryRepository,eventPublisher);
    }

    @Test
    void checkPrice_shouldDoNothing_whenNoTrackedItemFound() throws IOException {
        Mockito.when(trackedItemRepository.findAllByMarketplace(Marketplace.WB)).thenReturn(Optional.empty());

        wbTrackerService.checkPrice();

        verify(trackedItemRepository).findAllByMarketplace(Marketplace.WB);
        verifyNoMoreInteractions(trackedItemRepository);
        verifyNoInteractions(wbDataProvider,wbParser,priceHistoryRepository);
    }

    private List<TrackedItem> createTrackedItems(){
        TrackedItem item1 = TrackedItem.builder()
                .id(1L)
                .article(article + "1")
                .chatId(chatId)
                .marketplace(Marketplace.WB)
                .priceHistory(new ArrayList<>())
                .basicPrice(BigDecimal.valueOf(100))
                .currentPrice(BigDecimal.valueOf(120))
                .salePrice(null)
                .title(productName)
                .build();
        TrackedItem item2 = TrackedItem.builder()
                .id(2L)
                .article(article + "2")
                .chatId(chatId)
                .marketplace(Marketplace.WB)
                .priceHistory(new ArrayList<>())
                .basicPrice(BigDecimal.valueOf(100))
                .currentPrice(BigDecimal.valueOf(140))
                .salePrice(null)
                .title(productName)
                .build();
        TrackedItem item3 = TrackedItem.builder()
                .id(3L)
                .article(article + "3")
                .chatId(chatId)
                .marketplace(Marketplace.WB)
                .priceHistory(new ArrayList<>())
                .basicPrice(BigDecimal.valueOf(90))
                .currentPrice(BigDecimal.valueOf(160))
                .salePrice(null)
                .title(productName)
                .build();

        return List.of(item1,item2,item3);
    }



}