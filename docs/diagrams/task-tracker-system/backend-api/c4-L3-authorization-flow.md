graph TD
    subgraph "Data Access Flow"
        direction LR
        
        %% --- Входная точка ---
        AuthenticatedRequest("Аутентифицированный HTTP Запрос")
        
        subgraph "Web Layer"
            TaskController("TaskController")
            style TaskController fill:#0075A8,stroke:#004C6D,color:#fff
        end
        
        subgraph "Business Logic (Service Layer)"
            TaskService("TaskService")
            style TaskService fill:#4C8A4B,stroke:#376436,color:#fff
        end
        
        subgraph "Authorization Logic"
            MethodSecurity("Spring Method Security (e.g. @PreAuthorize)")
            RepositorySecurity("Repository-level Security (e.g. ... WHERE user_id = ?)")
            style MethodSecurity fill:#E09D00,stroke:#B07B00,color:#fff
            style RepositorySecurity fill:#E09D00,stroke:#B07B00,color:#fff
        end
        
        subgraph "Data Access Layer"
            TaskRepository("TaskRepository")
            style TaskRepository fill:#A0A0A0,stroke:#646464,color:#fff
        end
        
        %% --- Взаимодействия ---
        AuthenticatedRequest -- "GET /tasks/{id}" --> TaskController;
        
        TaskController -- "Получает userId из Principal, вызывает сервис" --> TaskService;
        
        TaskService -- "(опц.) Проверка прав на уровне метода" --> MethodSecurity;
        
        TaskService -- "Вызывает 'безопасный' метод репозитория" --> TaskRepository;
        
        TaskRepository -- "Включает userId в запрос" --> RepositorySecurity;
        
        RepositorySecurity -- "Фильтрует данные" --> DB("PostgreSQL Database");
        
    end