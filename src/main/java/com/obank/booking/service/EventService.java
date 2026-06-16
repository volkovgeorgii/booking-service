package com.obank.booking.service;

import com.obank.booking.domain.Event;
import com.obank.booking.domain.Seat;
import com.obank.booking.domain.SeatStatus;
import com.obank.booking.exception.EventNotFoundException;
import com.obank.booking.repository.EventRepository;
import com.obank.booking.repository.SeatRepository;
import com.obank.booking.web.dto.AvailabilityResponse;
import com.obank.booking.web.dto.CreateEventRequest;
import com.obank.booking.web.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = new Event(request.name(), request.type(), request.totalSeats());
        eventRepository.save(event);

        List<Seat> seats = IntStream.rangeClosed(1, request.totalSeats())
                .mapToObj(i -> new Seat(event.getId(), String.valueOf(i)))
                .toList();
        seatRepository.saveAll(seats);

        return EventResponse.from(event);
    }

    @Cacheable(value = "availability", key = "#eventId")
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        List<Seat> freeSeats = seatRepository
                .findByEventIdAndStatusOrderBySeatNumber(eventId, SeatStatus.FREE);

        return new AvailabilityResponse(
                eventId,
                event.getTotalSeats(),
                freeSeats.size(),
                freeSeats.stream().map(Seat::getSeatNumber).toList());
    }

    @CacheEvict(value = "availability", key = "#eventId")
    public void evictAvailabilityCache(UUID eventId) {
        // логика в аннотации
    }
}
