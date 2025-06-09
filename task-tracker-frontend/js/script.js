/**
 * Основной скрипт для фронтенд-приложения Task Tracker.
 * Использует jQuery для манипуляций с DOM и AJAX-запросов.
 *
 * На данном этапе реализовано:
 * 1. Управление видимостью модальных окон регистрации и авторизации.
 * 2. Обработка отправки формы регистрации.
 * 3. Взаимодействие с Backend API для отправки данных и обработки ответов.
 * 4. Сохранение и удаление JWT в localStorage.
 * 5. Обновление UI в зависимости от статуса аутентификации пользователя.
 */

$(document).ready(function() {

    // --- Константы и глобальные переменные ---
    const BASE_API_URL = 'http://localhost:8080/api/v1';
    const JWT_STORAGE_KEY = 'jwt_token';

    // --- Кэширование jQuery-объектов ---
    const $registerModal = $('#registerModal');
    const $loginModal = $('#loginModal');
    const $showRegisterModalBtn = $('#showRegisterModalBtn');
    const $showLoginModalBtn = $('#showLoginModalBtn');
    const $closeModalBtns = $('.close-modal-btn');

    // Элементы, связанные с состоянием UI
    const $authContainer = $('#authContainer'); // Контейнер с кнопками "Регистрация/Вход"
    const $userInfo = $('#userInfo');           // Контейнер с email и кнопкой "Выход"
    const $userEmailDisplay = $('#userEmailDisplay');
    const $logoutBtn = $('#logoutBtn');

    // Формы и их элементы для ошибок
    const $registerForm = $('#registerForm');
    const $loginForm = $('#loginForm');
    const $registerError = $('#registerError');
    const $loginError = $('#loginError');

    // --- Функции для управления состоянием UI ---

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь аутентифицирован".
     * @param {string} email - Email пользователя для отображения.
     */
    function showLoggedInState(email) {
        $userEmailDisplay.text(email);
        $authContainer.hide();
        $userInfo.css('display', 'flex'); // Используем flex для корректного отображения
    }

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь не аутентифицирован".
     */
    function showLoggedOutState() {
        $userEmailDisplay.text('');
        $userInfo.hide();
        $authContainer.show();
    }

    // --- Логика для управления модальными окнами ---

    $showRegisterModalBtn.on('click', () => $registerModal.css('display', 'flex'));
    $showLoginModalBtn.on('click', () => $loginModal.css('display', 'flex'));

    $closeModalBtns.on('click', function() {
        $(this).closest('.modal').css('display', 'none');
    });

    $(window).on('click', (event) => {
        if ($(event.target).is('.modal')) {
            $(event.target).css('display', 'none');
        }
    });

    // --- Логика для отправки формы РЕГИСТРАЦИИ ---
    $registerForm.on('submit', (event) => {
        // 1. Отменяем стандартное поведение формы
        event.preventDefault();

        // Очищаем предыдущие ошибки
        $registerError.text('');

        // 2. Собираем данные из полей формы
        const email = $registerForm.find('#registerEmail').val();
        const password = $registerForm.find('#registerPassword').val();
        const repeatPassword = $registerForm.find('#registerRepeatPassword').val();

        // 3. Простая клиентская валидация
        if (password !== repeatPassword) {
            $registerError.text('Пароли не совпадают.');
            return;
        }

        // 4. AJAX-запрос на регистрацию
        $.ajax({
            url: `${BASE_API_URL}/users/register`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ email, password, repeatPassword }), // Используем camelCase

            // 5. Обработчик успешного ответа
            success: (response) => {
                alert('Регистрация прошла успешно! Теперь вы можете войти.');

                // Сохраняем токен, т.к. регистрация сразу авторизует пользователя
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                console.log("JWT token saved to localStorage after registration.");

                // Закрываем модальное окно и сбрасываем форму
                $registerModal.css('display', 'none');
                $registerForm.trigger('reset');
                showLoggedInState(email);
            },
            // 6. Обработчик ошибочного ответа
            error: (jqXHR) => handleApiError(jqXHR, $registerError)
        });
    });

    // --- Форма АВТОРИЗАЦИИ ---
    $loginForm.on('submit', (event) => {
        event.preventDefault();
        $loginError.text('');

        const email = $loginForm.find('#loginEmail').val();
        const password = $loginForm.find('#loginPassword').val();

        $.ajax({
            url: `${BASE_API_URL}/auth/login`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ email, password }),
            success: (response) => {
                alert('Вход выполнен успешно!');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                $loginModal.css('display', 'none');
                $loginForm.trigger('reset');
                showLoggedInState(email); // Вызываем функцию для смены UI
            },
            error: (jqXHR) => handleApiError(jqXHR, $loginError)
        });
    });

    // --- Логика выхода (Logout) ---
    $logoutBtn.on('click', () => {
        localStorage.removeItem(JWT_STORAGE_KEY);
        alert('Вы вышли из системы.');
        showLoggedOutState(); // Вызываем функцию для смены UI
    });

// --- Вспомогательные функции ---

    /**
     * Обрабатывает ошибки от API и отображает их в указанном div.
     * @param {jqXHR} jqXHR - Объект jQuery XHR из колбэка error.
     * @param {jQuery} $errorElement - jQuery-объект div'а для вывода ошибки.
     */
    function handleApiError(jqXHR, $errorElement) {
        console.error("API call failed:", jqXHR);
        let errorMessage = 'Произошла неизвестная ошибка. Пожалуйста, попробуйте позже.';

        if (jqXHR.responseJSON && jqXHR.responseJSON.detail) {
            errorMessage = jqXHR.responseJSON.detail;
        } else if (jqXHR.responseJSON && jqXHR.responseJSON.title) {
            errorMessage = jqXHR.responseJSON.title;
        }

        $errorElement.text(errorMessage);
    }

    // --- Начальная проверка статуса аутентификации при загрузке страницы ---

    function checkInitialAuthState() {
        const token = localStorage.getItem(JWT_STORAGE_KEY);
        //TODO call /users/me to get user info
        if (token) {
            try {
                // Простое декодирование payload токена (без проверки подписи)
                const payload = JSON.parse(atob(token.split('.')[1]));
                if (payload.email) {
                    showLoggedInState(payload.email);
                } else {
                    localStorage.removeItem(JWT_STORAGE_KEY);
                    showLoggedOutState();
                }
            } catch (e) {
                console.error("Failed to parse JWT from localStorage. Clearing token.", e);
                localStorage.removeItem(JWT_STORAGE_KEY);
                showLoggedOutState();
            }
        } else {
            showLoggedOutState();
        }
    }
    // ---

    checkInitialAuthState(); // Вызываем проверку при загрузке страницы

    console.log("Task Tracker frontend initialized.");

});