package by.northdakota.markettracker.Core.Service;

import by.northdakota.markettracker.Core.Dto.TrackedItemDto;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface TrackerService {
    Optional<TrackedItemDto> startTracking(String article, Long chatId) throws IOException;
    void stopTracking(String article,Long chatId) throws IOException;
    List<TrackedItemDto> getUserTrackedItem(Long chatId);
    void checkPrice() throws IOException;
}
