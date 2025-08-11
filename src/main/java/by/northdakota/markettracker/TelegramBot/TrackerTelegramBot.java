package by.northdakota.markettracker.TelegramBot;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;
import by.northdakota.markettracker.Core.Entity.Notification;
import by.northdakota.markettracker.Core.Service.OzonTrackerService;
import by.northdakota.markettracker.Core.Service.WbTrackerService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class TrackerTelegramBot extends TelegramLongPollingBot {

    private final WbTrackerService wbTrackerService;
    private final OzonTrackerService ozonTrackerService;

    private static final Logger loggerBot = LoggerFactory.getLogger(TrackerTelegramBot.class);

    @Value("${telegram.bot.name}")
    private String botUsername;

    enum BotState {
        WAITING_FOR_ARTICLE,
        WAITING_FOR_DELETE,
        WAITING_FOR_MARKETPLACE,
        READY
    }

    private final Map<Long, BotState> userStates = new HashMap<>();
    private final Map<Long, String> userMarketplace = new HashMap<>();

    private final String startBot = "/start";
    private final String listBot = "/list";
    private final String deleteBot = "/delete";

    @Autowired
    private TrackerTelegramBot(@Value("${telegram.bot.token}") String botToken,
                               WbTrackerService wbTrackerService,
                               OzonTrackerService ozonTrackerService){
        super(botToken);
        this.wbTrackerService = wbTrackerService;
        this.ozonTrackerService = ozonTrackerService;
    }

    @Override
    public void onUpdateReceived(Update update){
        if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();

            if ("WB".equals(callbackData) || "OZON".equals(callbackData)) {
                userMarketplace.put(chatId, callbackData);
                userStates.put(chatId, BotState.WAITING_FOR_ARTICLE);
                sendMessage(chatId, "Вы выбрали маркетплейс: " + callbackData + ". Теперь введите артикул товара.");
                try {
                    execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String message = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getChat().getUserName();

        loggerBot.info("Received message: "+message + "от пользователя "+ username);

        BotState currentState = userStates.getOrDefault(chatId, BotState.READY);

        if (message.startsWith("/")) {
            switch (message) {
                case startBot -> {
                    if (currentState != BotState.READY) {
                        sendMessage(chatId, "Вы уже начали работу с ботом. Продолжайте, пожалуйста.");
                    } else {
                        String userName = update.getMessage().getChat().getUserName();
                        startCommand(chatId, userName);
                        userStates.put(chatId, BotState.WAITING_FOR_MARKETPLACE);
                    }
                }
                case "/reset" -> {
                    userStates.remove(chatId);
                    userMarketplace.remove(chatId);
                    sendMessage(chatId, "Состояние сброшено. Введите /start для начала.");
                }
                case listBot -> listCommand(chatId);
                case deleteBot -> {
                    sendMessage(chatId,"Введите артикул:");
                    userStates.put(chatId,BotState.WAITING_FOR_DELETE);
                }
                default -> sendMessage(chatId, "Неизвестная команда. Введите /start, /list, /delete или /reset");
            }
        } else {
            BotState state = userStates.getOrDefault(chatId, BotState.READY);
            if (state == BotState.WAITING_FOR_ARTICLE) {
                try {
                    String marketplace = userMarketplace.get(chatId);
                    if (marketplace == null) {
                        sendMessage(chatId, "Пожалуйста, выберите маркетплейс командой /start.");
                        return;
                    }
                    handleArticleInput(chatId, message, marketplace);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                userStates.put(chatId, BotState.READY);
            } else if(state == BotState.WAITING_FOR_DELETE){
                try {
                    deleteCommand(message,chatId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                userStates.put(chatId, BotState.READY);
            } else if(state == BotState.WAITING_FOR_MARKETPLACE){
                sendMessage(chatId, "Пожалуйста, выберите маркетплейс с помощью кнопок.");
            } else {
                sendMessage(chatId, "Неизвестная команда. Введите /start, /list или /delete");
            }
        }
    }


    @Override
    public String getBotUsername() {
        return botUsername;
    }

    private void startCommand(Long chatId, String userName) {
        String welcome = "Привет, " + userName + "! 👋\n" +
                "Пожалуйста, выберите маркетплейс:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(welcome);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(InlineKeyboardButton.builder().text("WB").callbackData("WB").build());
        rowInline.add(InlineKeyboardButton.builder().text("OZON").callbackData("OZON").build());

        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);

        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleArticleInput(Long chatId, String article, String marketplace) throws IOException {
        if(!article.matches("^[1-9][0-9]{5,10}$")){
            sendMessage(chatId, "некорректный артикул");
            return;
        }
        Optional<TrackedItemDto> itemOpt;
        if ("WB".equals(marketplace)) {
            itemOpt = wbTrackerService.startTracking(article, chatId);
        } else if ("OZON".equals(marketplace)) {
            itemOpt = ozonTrackerService.startTracking(article, chatId);
        } else {
            sendMessage(chatId, "Неизвестный маркетплейс.");
            return;
        }
        if(itemOpt.isEmpty()){
            sendMessage(chatId,"Ошибка :)");
            return;
        }
        TrackedItemDto item = itemOpt.get();
        sendMessage(chatId, "Товар с артикулом " + article + " добавлен в отслеживание ✅");
        String text;
        if(marketplace.equals("WB")){
            text = String.format("Ваш товар: \n %s \n Текущая цена: %.2f ₽ \t Базовая цена: %.2f ₽ \n",item.getTitle(),
                    item.getCurrentPrice().divide(BigDecimal.valueOf(100)).doubleValue(),
                    item.getBasicPrice().divide(BigDecimal.valueOf(100)).doubleValue());
        }else{
            text = String.format("Ваш товар: \n %s \n Текущая цена без карты: %d ₽ \n Текущая цена с картой: %d ₽ \n Базовая цена: %d ₽\n",
                    item.getTitle(),
                    item.getCurrentPrice().toBigInteger(),
                    item.getSalePrice().toBigInteger(),
                    item.getBasicPrice().toBigInteger());
        }

        sendMessage(chatId,text);
    }


    private void listCommand(Long chatId){
        List<TrackedItemDto> itemsWb = wbTrackerService.getUserTrackedItem(chatId);
        List<TrackedItemDto> itemsOzon = ozonTrackerService.getUserTrackedItem(chatId);
        if (itemsWb.isEmpty() && itemsOzon.isEmpty()) {
            sendMessage(chatId, "Вы пока не отслеживаете ни один товар.");
            return;
        }

        StringBuilder response = new StringBuilder("Ваши отслеживаемые товары:\n\n");
        int index = 0;
        for (int i = 0; i < itemsWb.size(); i++,index++) {
            TrackedItemDto item = itemsWb.get(i);
            response.append(String.format(
                    "%d. %s\nАртикул: %s\nТекущая цена: %.2f ₽\nБазовая цена: %.2f ₽\n\n",
                    i + 1,
                    item.getTitle(),
                    item.getArticle(),
                    item.getCurrentPrice().divide(BigDecimal.valueOf(100)).doubleValue(),
                    item.getBasicPrice().divide(BigDecimal.valueOf(100)).doubleValue()
            ));
        }
        for (int i = 0; i < itemsOzon.size(); i++,index++) {
            TrackedItemDto item = itemsOzon.get(i);
            response.append(String.format(
                    "%d. %s\nАртикул: %s\nТекущая цена без карты: %d ₽ \n Текущая цена с картой: %d ₽ \n Базовая цена: %d ₽\n\n",
                    index + 1,
                    item.getTitle(),
                    item.getArticle(),
                    item.getCurrentPrice().toBigInteger(),
                    item.getSalePrice().toBigInteger(),
                    item.getBasicPrice().toBigInteger()
            ));
        }
        sendMessage(chatId, response.toString());
    }

    private void deleteCommand(String article,Long chatId) throws IOException {
        wbTrackerService.stopTracking(article,chatId);
        sendMessage(chatId,"Отслеживание прекращено.");
    }

    @EventListener
    public void handleTelegramMessage(Notification event) {
        sendMessage(event.getChatId(), event.getMessage());
    }


    public void sendMessage(Long chatId, String text) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
