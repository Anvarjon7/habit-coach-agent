package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.service.ParticipantService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @PostMapping
    public Participant createParticipant(@Valid @RequestBody CreateParticipant request){
        Participant participant = new Participant();
        participant.setFirstName(request.firstName());
        participant.setTelegramUsername(request.telegramUsername());
        return participantService.createParticipant(participant);
    }

    @GetMapping
    public List<Participant> getAllParticipants() {
        return participantService.getAllParticipants();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Participant> getParticipantById(@PathVariable Long id) {
        return participantService.getParticipantById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    public record CreateParticipant(@NotBlank @Size(max=100) String firstName,
                                    @Size(max=255) String telegramUsername) {}
}
