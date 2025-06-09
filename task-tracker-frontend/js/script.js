// Используем обертку jQuery, чтобы убедиться, что DOM полностью загружен
// перед тем, как мы начнем навешивать обработчики событий.
$(document).ready(function() {

    // --- Получаем ссылки на DOM-элементы, с которыми будем работать ---
    // Модальные окна
    const registerModal = $('#registerModal');
    const loginModal = $('#loginModal');

    // Кнопки для открытия модальных окон
    const showRegisterModalBtn = $('#showRegisterModalBtn');
    const showLoginModalBtn = $('#showLoginModalBtn');

    // Кнопки/элементы для закрытия модальных окон
    // Мы можем выбрать все элементы с классом .close-modal-btn
    const closeModalBtns = $('.close-modal-btn');


    // --- Логика для открытия модальных окон ---

    // Показать модальное окно регистрации
    showRegisterModalBtn.on('click', function() {
        registerModal.css('display', 'flex'); // Используем flex, т.к. в CSS настроили центрирование через flexbox
    });

    // Показать модальное окно авторизации
    showLoginModalBtn.on('click', function() {
        loginModal.css('display', 'flex');
    });


    // --- Логика для закрытия модальных окон ---

    // Закрыть модальное окно при клике на крестик (кнопку закрытия)
    closeModalBtns.on('click', function() {
        // .closest('.modal') найдет ближайшего родителя с классом .modal и скроет его
        $(this).closest('.modal').css('display', 'none');
    });

    // (Опционально, но хорошая практика)
    // Закрыть модальное окно при клике на полупрозрачный фон (вне содержимого окна)
    $(window).on('click', function(event) {
        // event.target - это элемент, по которому кликнули
        if ($(event.target).is('.modal')) {
            $(event.target).css('display', 'none');
        }
    });

    // Сообщение в консоль, что все скрипты инициализированы
    console.log("Modal interaction logic initialized.");

}); // Конец document.ready