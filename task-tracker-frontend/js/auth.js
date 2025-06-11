
/**
 * Модуль для управления логикой аутентификации.
 * Инициализирует обработчики для форм регистрации и логина.
 * Этот модуль является "умным" слоем, который принимает решения
 * на основе ответов API и вызывает "глупые" UI-хелперы.
 */
function setupAuthHandlers() {
    const JWT_STORAGE_KEY = 'jwt_token';

    const $registerForm = $('#registerForm');
    const $loginForm = $('#loginForm');
    const $logoutBtn = $('#logoutBtn');

    // ===================================================================
    // Обработчик для формы РЕГИСТРАЦИИ
    // ===================================================================
    $registerForm.on('submit', (event) => {
        event.preventDefault();
        window.ui.clearFormErrors($registerForm);

        const email = $registerForm.find('#registerEmail').val();
        const password = $registerForm.find('#registerPassword').val();
        const repeatPassword = $registerForm.find('#registerRepeatPassword').val();

        window.taskTrackerApi.registerUser({ email, password, repeatPassword })
            .done((response) => {
                console.log('Registration successful:', response);
                window.ui.showToastNotification('Registration successful!', 'success');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                window.ui.$registerModal.css('display', 'none');
                $registerForm.trigger('reset');
                window.ui.showLoggedInState(email);
            })
            .fail((jqXHR) => {
                const problem = jqXHR.responseJSON;
                if (!problem) {
                    window.ui.showGeneralError($registerForm, 'An unknown network error occurred.');
                    return;
                }

                if (jqXHR.status === 409 && problem.conflictingEmail) {
                    const loginLink = $('<a href="#">log in</a>').on('click', (e) => {
                        e.preventDefault();
                        window.ui.$registerModal.css('display', 'none');
                        window.ui.clearFormErrors($registerForm);
                        window.ui.clearFormErrors($loginForm);
                        window.ui.$loginModal.css('display', 'flex');
                        $('#loginEmail').val(problem.conflictingEmail).focus();
                    });

                    const message = $("<span>A user with this email already exists. Try to </span>").append(loginLink).append(".");
                    window.ui.showGeneralError($registerForm, message);
                    return; // Завершаем, так как это уникальный сценарий
                }

                const errorMessage = problem.detail || problem.title || 'An error occurred during registration.';
                window.ui.showGeneralError($registerForm, errorMessage);

                // Если есть детали по полям - дополнительно их подсвечиваем.
                if (problem.invalidParams) {
                    window.ui.applyValidationErrors($registerForm, problem.invalidParams);
                }
            });
    });

    // ===================================================================
    // Обработчик для формы АВТОРИЗАЦИИ
    // ===================================================================
    $loginForm.on('submit', (event) => {
        event.preventDefault();
        window.ui.clearFormErrors($loginForm);

        const email = $loginForm.find('#loginEmail').val();
        const password = $loginForm.find('#loginPassword').val();

        window.taskTrackerApi.loginUser({ email, password })
            .done((response) => {
                console.log('Login successful:', response);
                window.ui.showToastNotification('Login successful!', 'success');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                window.ui.$loginModal.css('display', 'none');
                $loginForm.trigger('reset');
                window.ui.showLoggedInState(email);
            })
            .fail((jqXHR) => {
                const problem = jqXHR.responseJSON;
                if (!problem) {
                    window.ui.showGeneralError($loginForm, 'An unknown network error occurred.');
                    return;
                }

                const errorMessage = problem.detail || problem.title || 'An error occurred during login.';
                window.ui.showGeneralError($loginForm, errorMessage);

                // Если есть детали по полям - дополнительно их подсвечиваем.
                if (problem.invalidParams) {
                    window.ui.applyValidationErrors($loginForm, problem.invalidParams);
                }
            });
    });

    // ===================================================================
    // Обработчик для кнопки ВЫХОДА (Logout)
    // ===================================================================
    $logoutBtn.on('click', () => {
        localStorage.removeItem(JWT_STORAGE_KEY);
        window.ui.showToastNotification('You have been logged out.', 'success');
        window.ui.showLoggedOutState();
    });
}