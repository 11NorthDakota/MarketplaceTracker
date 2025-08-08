package by.northdakota.markettracker.TelegramBot;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;
import by.northdakota.markettracker.Core.Entity.Notification;
import by.northdakota.markettracker.Core.Service.TrackerService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.sound.midi.Track;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class TrackerTelegramBot extends TelegramLongPollingBot {

    private final TrackerService trackerService;

    private static final Logger loggerBot = LoggerFactory.getLogger(TrackerTelegramBot.class);

    @Value("${telegram.bot.name}")
    private String botUsername;

    enum BotState {
        WAITING_FOR_ARTICLE,
        WAITING_FOR_DELETE,
        READY
    }

    private final Map<Long, BotState> userStates = new HashMap<>();

    private final String startBot = "/start";
    private final String listBot = "/list";
    private final String deleteBot = "/delete";

    @Autowired
    private TrackerTelegramBot(@Value("${telegram.bot.token}") String botToken, TrackerService trackerService){
        super(botToken);
        this.trackerService = trackerService;
    }

    @Override
    public void onUpdateReceived(Update update){
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        String message = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        loggerBot.info("Received message: "+message);

        if (message.startsWith("/")) {
            switch (message) {
                case startBot -> {
                    String userName = update.getMessage().getChat().getUserName();
                    startCommand(chatId, userName);
                    userStates.put(chatId, BotState.WAITING_FOR_ARTICLE); // Ждем артикул
                }
                case listBot -> listCommand(chatId);
                case deleteBot -> {
                    sendMessage(chatId,"Введите артикул:");
                    userStates.put(chatId,BotState.WAITING_FOR_DELETE);
                }

            }
        } else {
            BotState state = userStates.getOrDefault(chatId, BotState.READY);
            if (state == BotState.WAITING_FOR_ARTICLE) {
                try {
                    handleArticleInput(chatId, message);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                userStates.put(chatId, BotState.READY);
            }
            else if(state == BotState.WAITING_FOR_DELETE){
                try {
                    deleteCommand(message, chatId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                userStates.put(chatId, BotState.READY);
            }
            else {
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
                "Отправь артикул товара, который хочешь отслеживать.";
        sendMessage(chatId, welcome);
    }

    private void handleArticleInput(Long chatId, String article) throws IOException {
        if(!article.matches("^[1-9][0-9]{5,10}$")){
            sendMessage(chatId, "некорректный артикул");
            return;
        }
        Optional<TrackedItemDto> itemOpt = trackerService.startTracking(article,chatId);
        if(itemOpt.isEmpty()){
            sendMessage(chatId,"Ошибка :)");
            return;
        }
        TrackedItemDto item = itemOpt.get();
        sendMessage(chatId, "Товар с артикулом " + article + " добавлен в отслеживание ✅");
        String text = String.format("Ваш товар: \n %s \n Текущая цена: %.2f ₽ \t Базовая цена: %.2f ₽ \n",item.getTitle(),
                item.getCurrentPrice().divide(BigDecimal.valueOf(100)).doubleValue(),
                item.getBasicPrice().divide(BigDecimal.valueOf(100)).doubleValue());
        sendMessage(chatId,text);
    }


    private void listCommand(Long chatId){
        List<TrackedItemDto> items = trackerService.getUserTrackedItem(chatId);
        if (items.isEmpty()) {
            sendMessage(chatId, "Вы пока не отслеживаете ни один товар.");
            return;
        }
        StringBuilder response = new StringBuilder("Ваши отслеживаемые товары:\n\n");

        for (int i = 0; i < items.size(); i++) {
            TrackedItemDto item = items.get(i);
            response.append(String.format(
                    "%d. %s\nАртикул: %s\nТекущая цена: %.2f ₽\nБазовая цена: %.2f ₽\n\n",
                    i + 1,
                    item.getTitle(),
                    item.getArticle(),
                    item.getCurrentPrice().divide(BigDecimal.valueOf(100)).doubleValue(),
                    item.getBasicPrice().divide(BigDecimal.valueOf(100)).doubleValue()
            ));
        }
        sendMessage(chatId, response.toString());
    }

    private void deleteCommand(String article,Long chatId) throws IOException {
        trackerService.stopTracking(article,chatId);
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
