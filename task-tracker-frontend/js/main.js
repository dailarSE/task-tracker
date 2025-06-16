/**
 * @file main.js
 * @description Основная точка входа в приложение.
 * Отвечает за инициализацию и запуск других модулей.
 */

$(document).ready(function() {
    // 1. Инициализация всех общих обработчиков UI (модальные окна, toast'ы).
    window.ui.init();

    // 2. Инициализация UI-компонентов, специфичных для задач (списки задач).
    window.tasksUi.init();

    // 3. Инициализация обработчиков логики аутентификации.
    setupAuthHandlers();

    // 4. Инициализация обработчиков логики задач.
    setupTaskHandlers();

    // 5. Проверка начального состояния аутентификации при загрузке.
    window.auth.checkInitialState();

    console.log("Task Tracker frontend initialized.");
});