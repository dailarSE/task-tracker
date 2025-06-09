/**
 * Точка входа в приложение. Инициализирует все обработчики и модули.
 */
$(document).ready(function() {

    // --- Инициализация обработчиков модальных окон ---
    $('#showRegisterModalBtn').on('click', () => window.ui.$registerModal.css('display', 'flex'));
    $('#showLoginModalBtn').on('click', () => window.ui.$loginModal.css('display', 'flex'));
    $('.close-modal-btn').on('click', function() {
        $(this).closest('.modal').css('display', 'none');
    });
    $(window).on('click', (event) => {
        if ($(event.target).is('.modal')) {
            $(event.target).css('display', 'none');
        }
    });

    // --- Инициализация обработчиков форм аутентификации ---
    setupAuthHandlers();

    // --- Проверка начального состояния UI ---
    window.ui.checkInitialAuthState();

    console.log("Task Tracker frontend initialized.");
});