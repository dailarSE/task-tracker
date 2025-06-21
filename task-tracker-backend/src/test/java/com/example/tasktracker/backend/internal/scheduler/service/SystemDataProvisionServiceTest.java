package com.example.tasktracker.backend.internal.scheduler.service;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import com.example.tasktracker.backend.task.repository.TaskQueryRepository;
import com.example.tasktracker.backend.user.repository.UserQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты для SystemDataProvisionService")
class SystemDataProvisionServiceTest {

    @Mock
    private UserQueryRepository mockUserQueryRepository;
    @Mock
    private SchedulerSupportApiProperties mockProperties;
    @Mock
    private TaskQueryRepository mockTaskQueryRepository;
    private final ObjectMapper realObjectMapper = new ObjectMapper();

    private SystemDataProvisionService service;

    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MAX_PAGE_SIZE = 5000;

    @BeforeEach
    void setUp() {
        service = new SystemDataProvisionService(mockUserQueryRepository, mockTaskQueryRepository, mockProperties, realObjectMapper);

        SchedulerSupportApiProperties.UserProcessingIdsEndpoint endpointProps = new SchedulerSupportApiProperties.UserProcessingIdsEndpoint();
        endpointProps.setDefaultPageSize(DEFAULT_PAGE_SIZE);
        endpointProps.setMaxPageSize(MAX_PAGE_SIZE);
        lenient().when(mockProperties.getUserProcessingIds()).thenReturn(endpointProps);

        // Общая настройка для всех тестов, где репозиторий может быть вызван
        lenient().when(mockUserQueryRepository.findUserIds(anyLong(), anyInt())).thenReturn(new SliceImpl<>(List.of()));
    }

    @Nested
    @DisplayName("Обработка лимитов")
    class LimitHandlingTests {

        @Test
        @DisplayName("Когда лимит не передан (null) -> должен использоваться defaultPageSize")
        void getUserIdsForProcessing_whenLimitIsNull_shouldUseDefaultPageSize() {
            // Act
            service.getUserIdsForProcessing(null, null);

            // Assert
            verify(mockUserQueryRepository).findUserIds(0L, DEFAULT_PAGE_SIZE);
        }

        @Test
        @DisplayName("Когда запрошенный лимит в пределах нормы -> должен использоваться запрошенный лимит")
        void getUserIdsForProcessing_whenRequestedLimitIsValid_shouldUseRequestedLimit() {
            // Arrange
            int requestedLimit = 50;

            // Act
            service.getUserIdsForProcessing(null, requestedLimit);

            // Assert
            verify(mockUserQueryRepository).findUserIds(0L, requestedLimit);
        }

        @Test
        @DisplayName("Когда запрошенный лимит превышает максимальный -> должен использоваться maxPageSize")
        void getUserIdsForProcessing_whenRequestedLimitExceedsMax_shouldUseMaxPageSize() {
            // Arrange
            int requestedLimit = 9999;

            // Act
            service.getUserIdsForProcessing(null, requestedLimit);

            // Assert
            verify(mockUserQueryRepository).findUserIds(0L, MAX_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("Обработка курсора и пагинации")
    class CursorAndPaginationTests {

        @Test
        @DisplayName("Первый запрос (курсор null) -> должен вернуть первую страницу и курсор на следующую")
        void getUserIdsForProcessing_whenCursorIsNull_shouldReturnFirstPageAndNextCursor() {
            // Arrange
            List<Long> ids = List.of(1L, 2L, 3L);
            Slice<Long> mockSlice = new SliceImpl<>(ids, PageRequest.of(0, 3), true); // hasNext = true
            when(mockUserQueryRepository.findUserIds(0L, 3)).thenReturn(mockSlice);

            // Act
            PaginatedUserIdsResponse response = service.getUserIdsForProcessing(null, 3);

            // Assert
            assertThat(response.getData()).isEqualTo(ids);
            assertThat(response.getPageInfo().isHasNextPage()).isTrue();

            String expectedCursorJson = "{\"lastId\":3}";
            String expectedEncodedCursor = Base64.getEncoder().encodeToString(expectedCursorJson.getBytes());
            assertThat(response.getPageInfo().getNextPageCursor()).isEqualTo(expectedEncodedCursor);
        }

        @Test
        @DisplayName("Запрос с курсором для промежуточной страницы -> должен вернуть данные и новый курсор")
        void getUserIdsForProcessing_whenCursorIsValidForIntermediatePage_shouldReturnDataAndNextCursor() {
            // Arrange
            String cursorJson = "{\"lastId\":10}";
            String encodedCursor = Base64.getEncoder().encodeToString(cursorJson.getBytes());
            List<Long> ids = List.of(11L, 12L);
            Slice<Long> mockSlice = new SliceImpl<>(ids, PageRequest.of(0, 2), true);
            when(mockUserQueryRepository.findUserIds(10L, 2)).thenReturn(mockSlice);

            // Act
            PaginatedUserIdsResponse response = service.getUserIdsForProcessing(encodedCursor, 2);

            // Assert
            assertThat(response.getData()).isEqualTo(ids);
            assertThat(response.getPageInfo().isHasNextPage()).isTrue();
            String expectedNextCursorJson = "{\"lastId\":12}";
            String expectedEncodedNextCursor = Base64.getEncoder().encodeToString(expectedNextCursorJson.getBytes());
            assertThat(response.getPageInfo().getNextPageCursor()).isEqualTo(expectedEncodedNextCursor);
        }

        @Test
        @DisplayName("Запрос с курсором для последней страницы -> должен вернуть данные, но без нового курсора")
        void getUserIdsForProcessing_whenCursorIsValidForLastPage_shouldReturnDataAndNoNextCursor() {
            // Arrange
            String cursorJson = "{\"lastId\":10}";
            String encodedCursor = Base64.getEncoder().encodeToString(cursorJson.getBytes());
            List<Long> ids = List.of(11L, 12L);
            Slice<Long> mockSlice = new SliceImpl<>(ids, PageRequest.of(0, 2), false); // hasNext = false
            when(mockUserQueryRepository.findUserIds(10L, 2)).thenReturn(mockSlice);

            // Act
            PaginatedUserIdsResponse response = service.getUserIdsForProcessing(encodedCursor, 2);

            // Assert
            assertThat(response.getData()).isEqualTo(ids);
            assertThat(response.getPageInfo().isHasNextPage()).isFalse();
            assertThat(response.getPageInfo().getNextPageCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("Обработка граничных и ошибочных случаев")
    class EdgeAndErrorCasesTests {

        @Test
        @DisplayName("Невалидный (битый) курсор -> должен обработать ошибку и запросить первую страницу")
        void getUserIdsForProcessing_whenCursorIsInvalid_shouldGracefullyHandleAndRequestFirstPage() {
            // Arrange
            String invalidCursor = "this-is-not-base64-or-json";
            List<Long> firstPageIds = List.of(1L, 2L);
            Slice<Long> mockSlice = new SliceImpl<>(firstPageIds, PageRequest.of(0, 2), false);
            when(mockUserQueryRepository.findUserIds(0L, 2)).thenReturn(mockSlice);

            // Act
            PaginatedUserIdsResponse response = service.getUserIdsForProcessing(invalidCursor, 2);

            // Assert
            verify(mockUserQueryRepository).findUserIds(0L, 2);
            assertThat(response.getData()).isEqualTo(firstPageIds);
        }

        @Test
        @DisplayName("Репозиторий возвращает пустой слайс -> ответ должен быть пустым без курсора")
        void getUserIdsForProcessing_whenRepositoryReturnsEmptySlice_shouldReturnEmptyResponseWithNoCursor() {
            // Arrange
            Slice<Long> emptySlice = new SliceImpl<>(List.of());
            when(mockUserQueryRepository.findUserIds(anyLong(), anyInt())).thenReturn(emptySlice);

            // Act
            PaginatedUserIdsResponse response = service.getUserIdsForProcessing(null, 10);

            // Assert
            assertThat(response.getData()).isEmpty();
            assertThat(response.getPageInfo().isHasNextPage()).isFalse();
            assertThat(response.getPageInfo().getNextPageCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("Метод getTaskReportsForUsers")
    class GetTaskReportsForUsersTests {

        @Test
        @DisplayName("При валидном запросе должен вызывать репозиторий и возвращать его результат")
        void whenRequestIsValid_shouldCallRepositoryAndReturnItsResult() {
            // Arrange
            Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant to = Instant.now();
            UserTaskReportRequest request = new UserTaskReportRequest(List.of(1L, 2L), from, to);

            List<UserTaskReport> expectedReports = List.of(new UserTaskReport(1L, "user1@test.com"));
            when(mockTaskQueryRepository.findTaskReportsForUsers(request.getUserIds(), from, to))
                    .thenReturn(expectedReports);

            // Act
            List<UserTaskReport> actualReports = service.getTaskReportsForUsers(request);

            // Assert
            assertThat(actualReports).isSameAs(expectedReports);
            verify(mockTaskQueryRepository).findTaskReportsForUsers(request.getUserIds(), from, to);
        }

        @Test
        @DisplayName("Когда список ID в запросе пуст, должен вернуть пустой список без вызова репозитория")
        void whenIdListIsEmpty_shouldReturnEmptyListWithoutCallingRepo() {
            // Arrange
            Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant to = Instant.now();
            UserTaskReportRequest request = new UserTaskReportRequest(Collections.emptyList(), from, to);

            // Act
            List<UserTaskReport> actualReports = service.getTaskReportsForUsers(request);

            // Assert
            assertThat(actualReports).isNotNull().isEmpty();
            // Проверяем, что взаимодействия с репозиторием не было
            verifyNoInteractions(mockTaskQueryRepository);
        }
    }
}