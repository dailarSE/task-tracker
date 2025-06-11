/**
 * Инициализирует все обработчики событий для модальных окон.
 * - Открытие окон по кнопкам.
 * - Закрытие по "крестику".
 * - Закрытие по клику на фон (с защитой от "протягивания" мышки).
 */
function initializeModalHandlers() {
    // Открытие окон
    $('#showRegisterModalBtn').on('click', () => window.ui.$registerModal.css('display', 'flex'));
    $('#showLoginModalBtn').on('click', () => window.ui.$loginModal.css('display', 'flex'));

    // Закрытие по "крестику"
    $('.close-modal-btn').on('click', function() {
        $(this).closest('.modal').css('display', 'none');
    });

    // Логика закрытия по клику на фон
    let mousedownOnBackdrop = false;
    $('.modal').on('mousedown', function(event) {
        if (event.target === this) {
            mousedownOnBackdrop = true;
        }
    });

    $('.modal').on('mouseup', function(event) {
        if (mousedownOnBackdrop && event.target === this) {
            $(this).css('display', 'none');
        }
        mousedownOnBackdrop = false;
    });
}
/**
 * Точка входа в приложение. Инициализирует все обработчики и модули.
 */
$(document).ready(function() {
    // 1. Инициализация UI-компонентов (модальные окна)
    initializeModalHandlers();

    // 2. Инициализация обработчиков логики аутентификации
    setupAuthHandlers();

    // 3. Проверка начального состояния аутентификации и обновление UI
    window.ui.checkInitialAuthState();

    console.log("Task Tracker frontend initialized.");
});