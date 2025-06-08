graph TD
    subgraph "Authentication Flow"
        direction LR
        
        subgraph "Web Layer"
            AuthController("AuthController")
            UserController_Auth("UserController")
            style AuthController fill:#0075A8,stroke:#004C6D,color:#fff
            style UserController_Auth fill:#0075A8,stroke:#004C6D,color:#fff
        end
        
        subgraph "Security Filter Chain"
            JwtAuthFilter("JwtAuthenticationFilter")
            style JwtAuthFilter fill:#B42222,stroke:#821818,color:#fff
        end
        
        subgraph "Authentication Service Layer"
            AuthService("AuthService")
            UserLoadingService("UserDetailsService")
            style AuthService fill:#4C8A4B,stroke:#376436,color:#fff
            style UserLoadingService fill:#4C8A4B,stroke:#376436,color:#fff
        end
        
        subgraph "JWT Components"
            JwtIssuer("JwtIssuer")
            JwtValidator("JwtValidator")
            JwtConverter("JwtAuthenticationConverter")
            JwtKeyService("JwtKeyService")
            JwtProperties("JwtProperties")
            style JwtProperties fill:#D8B038,stroke:#997C28,color:#fff
        end
        
        subgraph "Data Access"
            UserRepository_Auth("UserRepository")
            style UserRepository_Auth fill:#A0A0A0,stroke:#646464,color:#fff
        end
        
        %% --- Поток 1: Логин/Регистрация ---
        Client_Auth("Client") -- "POST /login (email, password)" --> AuthController;
        Client_Auth -- "POST /register" --> UserController_Auth;
        
        AuthController --> AuthService;
        UserController_Auth --> AuthService;
        
        AuthService -- "Проверяет креды" --> UserLoadingService;
        UserLoadingService -- "Загружает User" --> UserRepository_Auth;
        AuthService -- "Создает JWT" --> JwtIssuer;
        JwtIssuer -- "Использует" --> JwtKeyService;
        JwtIssuer -- "Использует" --> JwtProperties;
        
        AuthService -- "Возвращает AuthResponse" --> AuthController;
        
        %% --- Поток 2: Валидация JWT для последующих запросов ---
        Client_Auth -- "GET /tasks (Authorization: Bearer...)" --> JwtAuthFilter;
        JwtAuthFilter -- "Валидирует токен" --> JwtValidator;
        JwtValidator -- "Использует" --> JwtKeyService;
        JwtAuthFilter -- "Конвертирует в Authentication" --> JwtConverter;
        JwtConverter -- "Использует" --> JwtProperties;
        JwtAuthFilter -- "Передает аутентифицированный запрос дальше" --> NextFilter("Далее в цепочку...");
        
    end