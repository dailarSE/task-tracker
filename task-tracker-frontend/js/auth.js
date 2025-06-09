// js/auth.js

/**
 * Модуль для управления логикой аутентификации.
 */
function setupAuthHandlers() {
    const JWT_STORAGE_KEY = 'jwt_token';

    // Кэшируем элементы, с которыми работаем
    const $registerForm = $('#registerForm');
    const $loginForm = $('#loginForm');
    const $registerError = $('#registerError');
    const $loginError = $('#loginError');
    const $logoutBtn = $('#logoutBtn');

    // -- Форма РЕГИСТРАЦИИ --
    $registerForm.on('submit', (event) => {
        event.preventDefault();
        $registerError.text('');

        const email = $registerForm.find('#registerEmail').val();
        const password = $registerForm.find('#registerPassword').val();
        const repeatPassword = $registerForm.find('#registerRepeatPassword').val();

        if (password !== repeatPassword) {
            $registerError.text('Пароли не совпадают.');
            return;
        }

        // Вызываем функцию из api.js
        window.taskTrackerApi.registerUser({ email, password, repeatPassword })
            .done((response) => {
                alert('Регистрация прошла успешно! Вы автоматически вошли в систему.');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                $('#registerModal').css('display', 'none');
                $registerForm.trigger('reset');
                window.ui.showLoggedInState(email);
            })
            .fail((jqXHR) => {
                window.ui.handleApiError(jqXHR, $registerError);
            });
    });

    // -- Форма АВТОРИЗАЦИИ --
    $loginForm.on('submit', (event) => {
        event.preventDefault();
        $loginError.text('');

        const email = $loginForm.find('#loginEmail').val();
        const password = $loginForm.find('#loginPassword').val();

        // Вызываем функцию из api.js
        window.taskTrackerApi.loginUser({ email, password })
            .done((response) => {
                alert('Вход выполнен успешно!');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                $('#loginModal').css('display', 'none');
                $loginForm.trigger('reset');
                window.ui.showLoggedInState(email); // Вызываем функцию из ui.js
            })
            .fail((jqXHR) => {
                window.ui.handleApiError(jqXHR, $loginError); // Вызываем хелпер из ui.js
            });
    });

    // -- Логика выхода (Logout) --
    $logoutBtn.on('click', () => {
        localStorage.removeItem(JWT_STORAGE_KEY);
        alert('Вы вышли из системы.');
        window.ui.showLoggedOutState(); // Вызываем функцию из ui.js
    });
}