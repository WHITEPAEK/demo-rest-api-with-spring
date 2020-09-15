package com.tistory.whitepaek.restapiwithspring.events;

import com.tistory.whitepaek.restapiwithspring.accounts.Account;
import com.tistory.whitepaek.restapiwithspring.accounts.CurrentUser;
import com.tistory.whitepaek.restapiwithspring.index.IndexController;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.Optional;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Controller
@RequestMapping(value = "/api/events", produces = MediaTypes.HAL_JSON_VALUE)
public class EventController {

    private final EventRepository eventRepository;
    private final ModelMapper modelMapper;
    private final EventValidator eventValidator;

    public EventController(EventRepository eventRepository, ModelMapper modelMapper, EventValidator eventValidator) {
        this.eventRepository = eventRepository;
        this.modelMapper = modelMapper;
        this.eventValidator = eventValidator;
    }

    @PostMapping
    public ResponseEntity createEvent(@RequestBody @Valid EventDto eventDto, Errors errors, @CurrentUser Account currentUser) {

        if (errors.hasErrors()) {
            return badRequest(errors);
        }

        eventValidator.validate(eventDto, errors);
        if (errors.hasErrors()) {
            return badRequest(errors);
        }

        Event event = modelMapper.map(eventDto, Event.class);
        event.update();
        event.setManager(currentUser);
        Event newEvent = this.eventRepository.save(event);

        WebMvcLinkBuilder selfLinkBuilder = linkTo(EventController.class).slash(newEvent.getId());
        URI createdUri = selfLinkBuilder.toUri();

//        EventResource eventResource = new EventResource(event); // EntityModel 생성자를 사용하는 경우, 하지만 Deprecated..
        EntityModel<Event> eventResource = EntityModel.of(event, selfLinkBuilder.withSelfRel());
        eventResource.add(linkTo(EventController.class).withRel("query-events"));
        eventResource.add(selfLinkBuilder.withRel("update-event"));
        eventResource.add(Link.of("/docs/index.html#resources-events-create").withRel("profile"));

        return ResponseEntity.created(createdUri).body(eventResource);
    }

    @GetMapping
    public ResponseEntity queryEvents(Pageable pageable, PagedResourcesAssembler<Event> assembler, @CurrentUser Account account) {
        Page<Event> page = this.eventRepository.findAll(pageable);

        var pagedModel = assembler.toModel(page, entity -> EntityModel.of(entity, linkTo(EventController.class).slash(entity.getId()).withSelfRel()));
        pagedModel.add(Link.of("/docs/index.html#resources-events-list").withRel("profile"));
        if (account != null) {
            pagedModel.add(linkTo(EventController.class).withRel("create-event"));
        }

        return ResponseEntity.ok(pagedModel);
    }

    @GetMapping("/{id}")
    public ResponseEntity getEvent(@PathVariable Integer id, @CurrentUser Account currentUser) {
        Optional<Event> optionalEvent = this.eventRepository.findById(id);
        if (optionalEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Event event = optionalEvent.get();
        EntityModel<Event> eventResource = EntityModel.of(event, linkTo(EventController.class).slash(event.getId()).withSelfRel());
        eventResource.add(Link.of("/docs/index.html#resources-events-get").withRel("profile"));
        if (event.getManager().equals(currentUser)) {
            eventResource.add(linkTo(EventController.class).slash(event.getId()).withRel("update-event"));
        }

        return ResponseEntity.ok(eventResource);
    }

    @PutMapping("/{id}")
    public ResponseEntity updateEvent(@PathVariable Integer id, @RequestBody @Valid EventDto eventDto, Errors errors, @CurrentUser Account currentUser) {
        Optional<Event> optionalEvent = this.eventRepository.findById(id);
        if (optionalEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (errors.hasErrors()) {
            return badRequest(errors);
        }

        this.eventValidator.validate(eventDto, errors);
        if (errors.hasErrors()) {
            return badRequest(errors);
        }

        Event existingEvent = optionalEvent.get();
        if (!existingEvent.getManager().equals(currentUser)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        this.modelMapper.map(eventDto, existingEvent); // eventDto -> existingEvent
        Event savedEvent = this.eventRepository.save(existingEvent);

        EntityModel<Event> eventResource = EntityModel.of(savedEvent, linkTo(EventController.class).slash(savedEvent.getId()).withSelfRel());
        eventResource.add(Link.of("/docs/index.html#resources-events-update").withRel("profile"));

        return ResponseEntity.ok(eventResource);
    }

    private ResponseEntity badRequest(Errors errors) {
//        return ResponseEntity.badRequest().body(new ErrorsResource(errors)); // EntityModel 생성자를 사용하는 경우, 하지만 Deprecated..
        return ResponseEntity.badRequest().body(EntityModel.of(errors, linkTo(methodOn(IndexController.class).index()).withRel("index")));
    }

}
