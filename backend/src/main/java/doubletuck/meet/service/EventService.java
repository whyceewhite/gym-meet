package doubletuck.meet.service;

import doubletuck.meet.domain.Event;
import doubletuck.meet.dto.EventRequest;
import doubletuck.meet.dto.EventResponse;
import doubletuck.meet.exception.ConflictException;
import doubletuck.meet.repository.EventRepository;
import doubletuck.meet.repository.ParticipantRepository;
import doubletuck.meet.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;

    @Autowired
    public EventService(EventRepository eventRepository, SessionRepository sessionRepository,
            ParticipantRepository participantRepository) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
    }

    public Optional<EventResponse> findById(Long id) {
        return eventRepository.findById(id).map(EventResponse::from);
    }

    public Page<EventResponse> getPaginatedEntities(Pageable pageable) {
        return eventRepository.findAll(pageable).map(EventResponse::from);
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        checkForDuplicate(request, null);
        Event event = new Event();
        applyRequest(event, request);
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public Optional<EventResponse> update(Long id, EventRequest request) {
        return eventRepository.findById(id).map(existing -> {
            checkForDuplicate(request, id);
            applyRequest(existing, request);
            return EventResponse.from(eventRepository.save(existing));
        });
    }

    @Transactional
    public boolean deleteById(Long id) {
        if (eventRepository.findById(id).isEmpty()) {
            return false;
        }
        if (!sessionRepository.findByEventId(id).isEmpty() || !participantRepository.findByEventId(id).isEmpty()) {
            throw new ConflictException(
                    "Event " + id + " has dependent sessions or participants and cannot be deleted");
        }
        eventRepository.deleteById(id);
        return true;
    }

    private void checkForDuplicate(EventRequest request, Long excludeId) {
        Optional<Event> duplicate = (request.figEventNumber() != null && !request.figEventNumber().isBlank())
                ? eventRepository.findByFigEventNumber(request.figEventNumber())
                : eventRepository.findByTitleIgnoreCaseAndStartDate(request.title(), request.startDate());

        duplicate.filter(event -> !event.getId().equals(excludeId))
                .ifPresent(event -> {
                    throw new ConflictException((request.figEventNumber() != null && !request.figEventNumber().isBlank())
                            ? "An event with FIG event number '" + request.figEventNumber() + "' already exists"
                            : "An event titled '" + request.title() + "' starting " + request.startDate()
                                    + " already exists");
                });
    }

    private void applyRequest(Event event, EventRequest request) {
        event.setFigEventNumber(request.figEventNumber());
        event.setTitle(request.title());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setCity(request.city());
        event.setCountryCode(request.countryCode());
        event.setVenue(request.venue());
        event.setLevel(request.level());
    }
}
