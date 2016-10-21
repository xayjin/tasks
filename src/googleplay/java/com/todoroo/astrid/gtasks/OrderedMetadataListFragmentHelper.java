/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gtasks;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.ListView;

import com.commonsware.cwac.tlv.TouchListView.DropListener;
import com.commonsware.cwac.tlv.TouchListView.GrabberClickListener;
import com.commonsware.cwac.tlv.TouchListView.SwipeListener;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.activity.TaskListFragment;
import com.todoroo.astrid.adapter.TaskAdapter;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.dao.MetadataDao;
import com.todoroo.astrid.dao.TaskAttachmentDao;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.tags.TagService;
import com.todoroo.astrid.ui.DraggableListView;

import org.tasks.R;
import org.tasks.dialogs.DialogBuilder;
import org.tasks.preferences.Preferences;
import org.tasks.themes.ThemeCache;
import org.tasks.ui.CheckBoxes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

class OrderedMetadataListFragmentHelper {

    private final DisplayMetrics metrics = new DisplayMetrics();
    private final GtasksTaskListUpdater updater;
    private final DialogBuilder dialogBuilder;
    private final CheckBoxes checkBoxes;
    private final TagService tagService;
    private final ThemeCache themeCache;
    private final TaskListFragment fragment;

    private final Preferences preferences;
    private final TaskAttachmentDao taskAttachmentDao;
    private final TaskDao taskDao;
    private final MetadataDao metadataDao;

    private DraggableTaskAdapter taskAdapter;

    private GtasksList list;

    OrderedMetadataListFragmentHelper(Preferences preferences, TaskAttachmentDao taskAttachmentDao,
                                             TaskDao taskDao, MetadataDao metadataDao,
                                             TaskListFragment fragment, GtasksTaskListUpdater updater,
                                             DialogBuilder dialogBuilder, CheckBoxes checkBoxes, TagService tagService,
                                             ThemeCache themeCache) {
        this.preferences = preferences;
        this.taskAttachmentDao = taskAttachmentDao;
        this.taskDao = taskDao;
        this.metadataDao = metadataDao;
        this.fragment = fragment;
        this.updater = updater;
        this.dialogBuilder = dialogBuilder;
        this.checkBoxes = checkBoxes;
        this.tagService = tagService;
        this.themeCache = themeCache;
    }

    // --- ui component setup

    private Activity getActivity() {
        return fragment.getActivity();
    }

    private ListView getListView() {
        return fragment.getListView();
    }

    private DraggableListView getTouchListView() {
        return (DraggableListView) fragment.getListView();
    }

    void setUpUiComponents() {
        TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorAccent, tv, false);
        getTouchListView().setDragndropBackgroundColor(tv.data);
        getTouchListView().setDropListener(dropListener);
        getTouchListView().setClickListener(rowClickListener);
        getTouchListView().setSwipeListener(swipeListener);
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    public void beforeSetUpTaskList(Filter filter) {
    }

    private final DropListener dropListener = new DropListener() {
        @Override
        public void drop(int from, int to) {
            long targetTaskId = taskAdapter.getItemId(from);
            if (targetTaskId <= 0) {
                return; // This can happen with gestures on empty parts of the list (e.g. extra space below tasks)
            }
            long destinationTaskId = taskAdapter.getItemId(to);

            try {
                if(to >= getListView().getCount()) {
                    updater.moveTo(list, targetTaskId, -1);
                } else {
                    updater.moveTo(list, targetTaskId, destinationTaskId);
                }
            } catch (Exception e) {
                Timber.e(e, e.getMessage());
            }

            fragment.loadTaskListContent();
        }
    };

    private final SwipeListener swipeListener = new SwipeListener() {
        @Override
        public void swipeRight(int which) {
            indent(which, 1);
        }

        @Override
        public void swipeLeft(int which) {
            indent(which, -1);
        }

        void indent(int which, int delta) {
            long targetTaskId = taskAdapter.getItemId(which);
            if (targetTaskId <= 0) {
                return; // This can happen with gestures on empty parts of the list (e.g. extra space below tasks)
            }
            try {
                updater.indent(list, targetTaskId, delta);
            } catch (Exception e) {
                Timber.e(e, e.getMessage());
            }
            fragment.loadTaskListContent();
        }
    };

    private final GrabberClickListener rowClickListener = new GrabberClickListener() {
        @Override
        public void onLongClick(final View v) {
            if(v == null) {
                return;
            }

            fragment.registerForContextMenu(getListView());
            getListView().showContextMenuForChild(v);
            fragment.unregisterForContextMenu(getListView());
        }

        @Override
        public void onClick(View v) {
            if(v == null) {
                return;
            }
            taskAdapter.onClick(v);
        }
    };

    TaskAdapter createTaskAdapter(Context context, TodorooCursor<Task> cursor,
            AtomicReference<String> sqlQueryTemplate) {

        taskAdapter = new DraggableTaskAdapter(context, preferences, fragment, cursor,
                sqlQueryTemplate, dialogBuilder, checkBoxes, tagService, themeCache);

        taskAdapter.addOnCompletedTaskListener(this::setCompletedForItemAndSubtasks);

        return taskAdapter;
    }

    private final class DraggableTaskAdapter extends TaskAdapter {

        private DraggableTaskAdapter(Context context, Preferences preferences, TaskListFragment activity,
                                     Cursor c, AtomicReference<String> query, DialogBuilder dialogBuilder,
                                     CheckBoxes checkBoxes, TagService tagService, ThemeCache themeCache) {
            super(context, preferences, taskAttachmentDao, taskDao, activity, c, query,
                    dialogBuilder, checkBoxes, tagService, themeCache);
        }

        @Override
        public synchronized void setFieldContentsAndVisibility(View view) {
            super.setFieldContentsAndVisibility(view);

            ViewHolder vh = (ViewHolder) view.getTag();
            int indent = vh.task.getValue(GtasksMetadata.INDENT);
            vh.rowBody.setPadding(Math.round(indent * 20 * metrics.density), 0, 0, 0);
        }
    }

    private final Map<Long, ArrayList<Long>> chainedCompletions =
        Collections.synchronizedMap(new HashMap<Long, ArrayList<Long>>());

    private void setCompletedForItemAndSubtasks(final Task item, final boolean completedState) {
        final long itemId = item.getId();

        final Task model = new Task();
        final long completionDate = completedState ? DateUtilities.now() : 0;

        if(!completedState) {
            ArrayList<Long> chained = chainedCompletions.get(itemId);
            if(chained != null) {
                for(Long taskId : chained) {
                    model.setId(taskId);
                    model.setCompletionDate(completionDate);
                    taskDao.save(model);
                    model.clear();
                }
                taskAdapter.notifyDataSetInvalidated();
            }
            return;
        }

        final ArrayList<Long> chained = new ArrayList<>();
        final int parentIndent = item.getValue(GtasksMetadata.INDENT);
        updater.applyToChildren(list, itemId, node -> {
            Task childTask = taskDao.fetch(node.taskId, Task.RECURRENCE);
            if(!TextUtils.isEmpty(childTask.getRecurrence())) {
                Metadata metadata = updater.getTaskMetadata(node.taskId);
                metadata.setValue(GtasksMetadata.INDENT, parentIndent);
                metadataDao.persist(metadata);
            }

            model.setId(node.taskId);
            model.setCompletionDate(completionDate);
            taskDao.save(model);
            model.clear();

            chained.add(node.taskId);
        });

        if(chained.size() > 0) {
            chainedCompletions.put(itemId, chained);
            taskAdapter.notifyDataSetInvalidated();
        }
    }

    public void setList(GtasksList list) {
        this.list = list;
    }

    public void onCreateTask(String uuid) {
        //
    }

    void onDeleteTask(Task task) {
        updater.onDeleteTask(list, task.getId());
        taskAdapter.notifyDataSetInvalidated();
    }
}
