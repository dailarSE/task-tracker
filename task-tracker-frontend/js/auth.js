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

    /**
     * Выполняется после любого успешного входа в систему.
     * Обновляет UI и запускает загрузку данных пользователя.
     * @param {string} email - Email вошедшего пользователя.
     * @private
     */
    function _onLoginSuccess(email) {
        window.ui.showLoggedInState(email);

        window.tasks.loadAll()
            .fail((jqXHR) => {
                console.error("Critical failure: Could not load tasks after login.", jqXHR.responseJSON);
                const problem = jqXHR.responseJSON;
                const message = problem?.detail || 'Could not load your tasks. Please try refreshing the page.';
                // Используем глобальное событие ошибки
                window.eventBus.trigger('app:error', { message: message });
            });
    }

    /**
     * Централизованная функция для выполнения логаута.
     * idempotent: многократный вызов не меняет результат после первого успешного выполнения.
     */
    function handleLogout() {
        const token = localStorage.getItem(JWT_STORAGE_KEY);
        if (!token) return;
        localStorage.removeItem(JWT_STORAGE_KEY);
        window.ui.resetAllUIStateToLoggedOut();

    }

    // --- Публичный API модуля auth ---
    window.auth = {
        /**
         * Проверяет начальное состояние аутентификации при загрузке страницы.
         */
        checkInitialState: function() {
            window.ui.checkInitialAuthState()
                .done((user) => {
                    _onLoginSuccess(user.email);
                });
            // Ошибки (401, нет токена) уже обработаны в ui.checkInitialAuthState или api.js
        },
        handleLogout: handleLogout
    };

    // ===================================================================
    // Обработчик для формы РЕГИСТРАЦИИ
    // ===================================================================
    $registerForm.on('submit', (event) => {
        event.preventDefault();
        window.ui.clearFormErrors($registerForm);
        window.ui.lockForm($registerForm);

        const email = $registerForm.find('#registerEmail').val();
        const password = $registerForm.find('#registerPassword').val();
        const repeatPassword = $registerForm.find('#registerRepeatPassword').val();

        window.taskTrackerApi.registerUser({email, password, repeatPassword})
            .done((response) => {
                console.log('Registration successful:', response);
                window.ui.showToastNotification('Registration successful!', 'success');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                window.ui.$registerModal.css('display', 'none');
                $registerForm.trigger('reset');
                _onLoginSuccess(email);
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
            })
            .always(() => {
                window.ui.unlockForm($registerForm);
            });
    });

    // ===================================================================
    // Обработчик для формы АВТОРИЗАЦИИ
    // ===================================================================
    $loginForm.on('submit', (event) => {
        event.preventDefault();
        window.ui.clearFormErrors($loginForm);
        window.ui.lockForm($loginForm);

        const email = $loginForm.find('#loginEmail').val();
        const password = $loginForm.find('#loginPassword').val();

        window.taskTrackerApi.loginUser({email, password})
            .done((response) => {
                console.log('Login successful:', response);
                window.ui.showToastNotification('Login successful!', 'success');
                localStorage.setItem(JWT_STORAGE_KEY, response.accessToken);
                window.ui.$loginModal.css('display', 'none');
                $loginForm.trigger('reset');
                _onLoginSuccess(email);
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
            })
            .always(() => {
                window.ui.unlockForm($loginForm);
            });
    });

    // ===================================================================
    // Обработчик для кнопки ВЫХОДА (Logout)
    // ===================================================================
    $logoutBtn.on('click', handleLogout);

}