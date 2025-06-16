/**
 * Модуль для управления UI-компонентами, связанными с задачами.
 */
window.tasksUi = {
    // --- Кэшированные элементы UI ---
    $tasksContainer: $('#tasksContainer'),
    $createTaskForm: $('#createTaskForm'),
    $newTaskTitleInput: $('#newTaskTitle'),
    $undoneTasksList: $('#undoneTasksList'),
    $doneTasksList: $('#doneTasksList'),

    init: function() {
        window.eventBus.on('tasks:refreshed', (e, tasks) => this.renderTaskLists(tasks));
        window.eventBus.on('task:updated', (e, task) => this.renderOrUpdateTask(task));
        window.eventBus.on('task:deleted', (e, taskData) => this.removeTaskElement(taskData.id));
    },

    show: function () {
        this.$tasksContainer.show();
    },
    hide: function () {
        this.$tasksContainer.hide();
    },

    /**
     * Создает HTML-разметку для одной задачи.
     * @param {object} task - Объект задачи, полученный от API.
     * @returns {string} HTML-строка для <li> элемента.
     */
    renderTask: function (task) {
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
     * Очищает и заполняет списки задач на странице.
     * @param {Array<object>} tasks - Массив объектов задач.
     */
    renderTaskLists: function(tasks) {
        this.$undoneTasksList.empty();
        this.$doneTasksList.empty();
        if (tasks) {
            tasks.forEach(task => this.renderOrUpdateTask(task));
        }
    },

    /**
     * Рендерит или обновляет один DOM-элемент задачи в списке.
     *
     * Если элемент с `data-task-id` уже существует в DOM, он будет заменен
     * новым отрендеренным HTML. Это гарантирует, что все данные (title,
     * статус, и т.д.) будут актуальными.
     *
     * Если элемент не найден, он будет создан и добавлен в начало
     * соответствующего списка (выполненные/невыполненные) в зависимости
     * от статуса задачи.
     *
     * После любого изменения вызывает сортировку списков для поддержания порядка.
     *
     * @param {object} task - Объект задачи, полученный из Store.
     */
    renderOrUpdateTask: function(task) {
        const $existingItem = $(`li[data-task-id="${task.id}"]`);
        const taskHtml = this.renderTask(task);
        if ($existingItem.length) {
            $existingItem.replaceWith(taskHtml);
        } else {
            const $list = task.status === 'COMPLETED' ? this.$doneTasksList : this.$undoneTasksList;
            $list.prepend(taskHtml);
        }
        this.sortTaskList(this.$undoneTasksList);
        this.sortTaskList(this.$doneTasksList);
    },

    /**
     * Динамически перемещает элемент задачи и сортирует списки ПОСЛЕ анимации.
     * @param {number} taskId - ID задачи для перемещения.
     * @param {string} newStatus - Новый статус ('PENDING' или 'COMPLETED').
     */
    moveTaskElement: function (taskId, newStatus) {
        const $taskItem = $(`li[data-task-id="${taskId}"]`);
        if ($taskItem.length === 0) return;

        const isCompleted = newStatus === 'COMPLETED';
        const $targetList = isCompleted ? this.$doneTasksList : this.$undoneTasksList;
        const self = this;

        $taskItem.fadeOut(200, function () {
            const $movedItem = $(this);
            $movedItem.hide();

            $targetList.prepend($movedItem);

            self.sortTaskList($targetList);

            if (isCompleted) {
                $movedItem.addClass('done');
            } else {
                $movedItem.removeClass('done');
            }
            $movedItem.fadeIn(200);
        });
    },

    /**
     * Сортирует задачи внутри указанного jQuery-элемента списка.
     * Сортировка происходит по ID задачи (data-task-id) в порядке убывания (новые вверху).
     * @param {jQuery} $list - jQuery-объект <ul> для сортировки.
     */
    sortTaskList: function ($list) {
        const items = $list.children('li:not(.placeholder-item)');
        const itemsArray = items.get();
        itemsArray.sort(function (a, b) {
            const idA = parseInt(a.getAttribute('data-task-id'), 10);
            const idB = parseInt(b.getAttribute('data-task-id'), 10);
            return idB - idA; // Сортировка по убыванию ID
        });
        $list.append(itemsArray);
    },

    removeTaskElement: function(taskId) {
        $(`li[data-task-id="${taskId}"]`).fadeOut(200, function() { $(this).remove(); });
    },

    clearCreateTaskForm: function () {
        window.ui.clearFormErrors(this.$createTaskForm);
        this.$newTaskTitleInput.val('');
    }
};