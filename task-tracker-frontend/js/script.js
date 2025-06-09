/**
 * Основной скрипт для фронтенд-приложения Task Tracker.
 * Использует jQuery для манипуляций с DOM и AJAX-запросов.
 *
 * На данном этапе реализовано:
 * 1. Управление видимостью модальных окон регистрации и авторизации.
 * 2. Обработка отправки формы регистрации.
 */

$(document).ready(function() {

    // --- Константы ---
    const BASE_API_URL = 'http://localhost:8080/api/v1'; // Базовый URL бэкенда.
    const JWT_STORAGE_KEY = 'jwt_token'; // Ключ для хранения токена в localStorage

    // --- Кэширование jQuery-объектов ---
    const $registerModal = $('#registerModal');
    const $loginModal = $('#loginModal');
    const $showRegisterModalBtn = $('#showRegisterModalBtn');
    const $showLoginModalBtn = $('#showLoginModalBtn');
    const $closeModalBtns = $('.close-modal-btn');
    const $registerForm = $('#registerForm');
    const $registerError = $('#registerError');

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

                // TODO: обновиmь UI, чтобы показать email пользователя и скрыть кнопки регистрации/авторизации.
            },

            // 6. Обработчик ошибочного ответа
            error: (jqXHR) => {
                console.error("Registration failed:", jqXHR);
                let errorMessage = 'Произошла неизвестная ошибка.';

                if (jqXHR.responseJSON && jqXHR.responseJSON.detail) {
                    errorMessage = jqXHR.responseJSON.detail;
                } else if (jqXHR.responseJSON && jqXHR.responseJSON.title) {
                    errorMessage = jqXHR.responseJSON.title;
                }

                $registerError.text(errorMessage);
            }
        });
    });

    console.log("Task Tracker frontend scripts initialized.");
});