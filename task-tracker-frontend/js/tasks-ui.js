/**
 * @file tasks-ui.js
 * @description
 * Этот файл отвечает исключительно за рендеринг и обновление DOM-элементов,
 * связанных с задачами. Он является "тупым" слоем UI, который реагирует
 * на события, генерируемые `tasks-store.js` через `event-bus.js`.
 * Он не содержит бизнес-логики или прямых вызовов API.
 */

/**
 * Глобальный объект `tasksUi`, инкапсулирующий всю логику рендеринга задач.
 * @namespace tasksUi
 */
window.tasksUi = {
    // --- Кэшированные jQuery-объекты для элементов UI ---
    $tasksContainer: $('#tasksContainer'),
    $createTaskForm: $('#createTaskForm'),
    $newTaskTitleInput: $('#newTaskTitle'),
    $undoneTasksList: $('#undoneTasksList'),
    $doneTasksList: $('#doneTasksList'),
    $taskEditModal: $('#taskEditModal'),
    $saveIndicator: $('#saveIndicator'),

    /**
     * Инициализирует модуль, подписываясь на необходимые события от EventBus.
     * Эта функция должна вызываться один раз при старте приложения из `main.js`.
     */
    init: function() {
        window.eventBus.on('tasks:refreshed', (e, tasks) => this.renderTaskLists(tasks));
        window.eventBus.on('task:added', (e, task) => this.addTaskElement(task));
        window.eventBus.on('task:title-updated', (e, task) => this.updateTaskTitle(task));
        window.eventBus.on('task:status-changed', (e, task) => this.moveTaskElement(task));
        window.eventBus.on('task:deleted', (e, taskData) => this.removeTaskElement(taskData.id));
        console.log("Tasks UI event listeners initialized.");
    },

    /**
     * Показывает основной контейнер с задачами.
     */
    show: function() {
        this.$tasksContainer.show();
    },

    /**
     * Скрывает основной контейнер с задачами.
     */
    hide: function() {
        this.$tasksContainer.hide();
    },

    /**
     * Генерирует HTML-разметку для одной задачи (элемента <li>).
     * @param {object} task - Объект задачи.
     * @returns {string} HTML-строка для <li>.
     */
    renderTask: function(task) {
        const isDone = task.status === 'COMPLETED';
        const checkedAttr = isDone ? 'checked' : '';
        const doneClass = isDone ? 'class="done"' : '';
        const escapedTitle = $('<div/>').text(task.title).html();

        return `<li data-task-id="${task.id}" ${doneClass}>
                    <label>
                        <input type="checkbox" class="task-checkbox" ${checkedAttr}>
                    </label>
                    <span class="task-title">${escapedTitle}</span>
                    <button class="delete-task-btn" aria-label="Delete task">×</button>
                </li>`;
    },

    /**
     * Полностью очищает и перерисовывает оба списка задач на странице.
     * @param {Array<object>} tasks - Массив всех задач из Store.
     */
    renderTaskLists: function(tasks) {
        this.$undoneTasksList.empty();
        this.$doneTasksList.empty();
        if (tasks) {
            tasks.forEach(task => {
                const taskHtml = this.renderTask(task);
                const $list = task.status === 'COMPLETED' ? this.$doneTasksList : this.$undoneTasksList;
                $list.append(taskHtml);
            });
            this.sortTaskList(this.$undoneTasksList);
            this.sortTaskList(this.$doneTasksList);
        }
    },

    /**
     * Просто добавляет новый элемент задачи в нужный список.
     * @param {object} task - Объект новой задачи.
     */
    addTaskElement: function(task) {
        const taskHtml = this.renderTask(task);
        const $list = task.status === 'COMPLETED' ? this.$doneTasksList : this.$undoneTasksList;
        $list.prepend(taskHtml).hide().fadeIn(200); // Добавляем с небольшим эффектом
        this.sortTaskList($list);
    },

    /**
     * Просто заменяет КОНТЕНТ (title) существующего элемента задачи.
     * @param {object} task - Объект обновленной задачи.
     */
    replaceTaskElementContent: function(task) {
        // Находим существующий элемент
        const $existingItem = $(`li[data-task-id="${task.id}"]`);
        if (!$existingItem.length) return; // Если элемента нет, ничего не делаем

        // Экранируем новый заголовок
        const escapedTitle = $('<div/>').text(task.title).html();
        // Находим и обновляем только span с текстом
        $existingItem.find('.task-title').html(escapedTitle);
        console.log(`Task ${task.id} content updated in UI.`);
    },

    /**
     * Заменяет заголовок существующего элемента задачи.
     * @param {object} task - Объект обновленной задачи.
     */
    updateTaskTitle: function(task) {
        const $existingItem = $(`li[data-task-id="${task.id}"]`);
        if (!$existingItem.length) return;

        const escapedTitle = $('<div/>').text(task.title).html();
        $existingItem.find('.task-title').html(escapedTitle);
        console.log(`Task ${task.id} title updated in UI.`);
    },

    /**
     * Просто перемещает элемент задачи из одного списка в другой.
     * @param {object} task - Объект перемещаемой задачи с НОВЫМ статусом.
     */
    moveTaskElement: function(task) {
        const $taskItem = $(`li[data-task-id="${task.id}"]`);
        if (!$taskItem.length) return;

        const shouldBeDone = task.status === 'COMPLETED';
        const isCurrentlyDone = $taskItem.parent().is(this.$doneTasksList);

        // Перемещаем, только если элемент находится не в том списке
        if (shouldBeDone === isCurrentlyDone) return;

        const $targetList = shouldBeDone ? this.$doneTasksList : this.$undoneTasksList;
        const self = this;

        $taskItem.fadeOut(200, function() {
            // Обновляем состояние чекбокса и класса перед перемещением
            const $movedItem = $(this);
            $movedItem.find('.task-checkbox').prop('checked', shouldBeDone);
            $movedItem.toggleClass('done', shouldBeDone);

            $targetList.prepend($movedItem);
            self.sortTaskList($targetList);
            $movedItem.fadeIn(200);
            console.log(`Task ${task.id} moved to ${shouldBeDone ? 'done' : 'undone'} list.`);
        });
    },

    /**
     * Удаляет DOM-элемент задачи со страницы с эффектом затухания.
     * @param {number} taskId - ID задачи для удаления.
     */
    removeTaskElement: function(taskId) {
        $(`li[data-task-id="${taskId}"]`).fadeOut(200, function() { $(this).remove(); });
    },

    /**
     * Сортирует задачи внутри указанного списка по ID (новые вверху).
     * @param {jQuery} $list - jQuery-объект <ul> для сортировки.
     */
    sortTaskList: function($list) {
        const items = $list.children('li');
        items.sort(function(a, b) {
            const idA = parseInt(a.getAttribute('data-task-id'), 10);
            const idB = parseInt(b.getAttribute('data-task-id'), 10);
            return idB - idA; // Сортировка по убыванию ID
        });
        $list.append(items);
    },

    /**
     * Очищает форму создания новой задачи.
     */
    clearCreateTaskForm: function() {
        window.ui.clearFormErrors(this.$createTaskForm);
        this.$newTaskTitleInput.val('');
    },

    /**
     * Показывает индикатор "Сохранение...".
     */
    showSavingIndicator: function() {
        this.$saveIndicator.text('Сохранение...').addClass('show');
    },

    /**
     * Показывает индикатор "Сохранено" и плавно скрывает его.
     */
    showSavedIndicator: function() {
        this.$saveIndicator.text('Сохранено');
        this.$saveIndicator.addClass('show');
        setTimeout(() => {
            this.$saveIndicator.removeClass('show');
        }, 2000); // Скрываем через 2 секунды
    },

    /**
     * Немедленно скрывает индикатор сохранения.
     */
    hideSaveIndicator: function() {
        this.$saveIndicator.removeClass('show');
    },

    /**
     * Заполняет данными и показывает модальное окно редактирования задачи.
     * Этот метод не содержит логики обработчиков событий.
     * @param {object} task - Объект задачи из Store.
     */
    showEditModal: function(task) {
        const $modal = this.$taskEditModal;

        $modal.find('#editTaskId').val(task.id);
        $modal.find('#editTaskTitle').val(task.title);
        $modal.find('#editTaskDescription').val(task.description || '');
        $modal.find('#editTaskStatus').prop('checked', task.status === 'COMPLETED');

        window.ui.clearFormErrors($modal);
        $modal.find('.conflict-resolver').hide().empty();
        $modal.find('#saveIndicator').removeClass('show');

        $modal.find('form').data('originalTask', task);

        $modal.css('display', 'flex');
    }
};