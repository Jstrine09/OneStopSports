package com.onestopsports.service;

import com.onestopsports.dto.SportDto;
import com.onestopsports.model.Sport;
import com.onestopsports.repository.SportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Pure unit tests for SportService — no Spring context, no database.
// SportService has a single dependency (SportRepository), which is mocked below so the
// test never touches a real DB — same @InjectMocks/@Mock convention as MatchServiceTest,
// just scaled down to one collaborator.
//
// What's being pinned here:
//   1. getAllSports maps every repository row to a SportDto, preserving all four fields.
//   2. getAllSports returns an empty list (not null, not an exception) when there's no data.
//   3. getSportBySlug's happy path returns the mapped DTO.
//   4. getSportBySlug's 404 guard — an unknown slug throws ResponseStatusException.
@ExtendWith(MockitoExtension.class)
class SportServiceTest {

    // The only dependency SportService has — mocked so no real DB is touched
    @Mock private SportRepository sportRepository;

    // @InjectMocks builds a real SportService and injects the mock above via its constructor
    @InjectMocks
    private SportService sportService;

    private static Sport fixtureSport(Long id, String name, String slug) {
        return Sport.builder().id(id).name(name).slug(slug).iconUrl("https://icons.example/" + slug + ".png").build();
    }

    // ── getAllSports ──────────────────────────────────────────────────────────

    @Test
    void getAllSports_mapsEachRepositoryRowToMatchingSportDto() {
        // GIVEN: two Sport entities in the "database"
        Sport football = fixtureSport(1L, "Futbol", "football");
        Sport basketball = fixtureSport(2L, "Basketball", "basketball");
        when(sportRepository.findAll()).thenReturn(List.of(football, basketball));

        // WHEN
        List<SportDto> result = sportService.getAllSports();

        // THEN: each DTO's id/name/slug/iconUrl matches the entity it was mapped from,
        // in the same order the repository returned them
        assertThat(result).containsExactly(
                new SportDto(1L, "Futbol", "football", "https://icons.example/football.png"),
                new SportDto(2L, "Basketball", "basketball", "https://icons.example/basketball.png")
        );
    }

    @Test
    void getAllSports_noRowsInRepository_returnsEmptyList() {
        // GIVEN: the repository has nothing to return
        when(sportRepository.findAll()).thenReturn(List.of());

        // WHEN
        List<SportDto> result = sportService.getAllSports();

        // THEN: an empty list, not null and not an exception
        assertThat(result).isEmpty();
    }

    // ── getSportBySlug ────────────────────────────────────────────────────────

    @Test
    void getSportBySlug_knownSlug_returnsMappedSportDto() {
        // GIVEN: a sport exists with this slug
        Sport football = fixtureSport(1L, "Futbol", "football");
        when(sportRepository.findBySlug("football")).thenReturn(Optional.of(football));

        // WHEN
        SportDto result = sportService.getSportBySlug("football");

        // THEN
        assertThat(result).isEqualTo(new SportDto(1L, "Futbol", "football", "https://icons.example/football.png"));
    }

    @Test
    void getSportBySlug_unknownSlug_throws404WithSportNotFoundMessage() {
        // GIVEN: no sport with this slug exists
        when(sportRepository.findBySlug("cricket")).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> sportService.getSportBySlug("cricket"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Sport not found");
    }
}
