package com.habitrpg.android.habitica.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableArrayList;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.habitrpg.android.habitica.R;
import com.habitrpg.android.habitica.databinding.DailyItemCardBinding;
import com.habitrpg.android.habitica.databinding.HabitItemCardBinding;
import com.habitrpg.android.habitica.databinding.RewardItemCardBinding;
import com.habitrpg.android.habitica.databinding.TodoItemCardBinding;
import com.habitrpg.android.habitica.events.HabitScoreEvent;
import com.habitrpg.android.habitica.events.TaskCreatedEvent;
import com.habitrpg.android.habitica.events.TaskLongPressedEvent;
import com.habitrpg.android.habitica.events.TaskRemovedEvent;
import com.habitrpg.android.habitica.events.TaskSaveEvent;
import com.habitrpg.android.habitica.events.TaskTappedEvent;
import com.habitrpg.android.habitica.events.TaskUpdatedEvent;
import com.habitrpg.android.habitica.events.commands.BuyRewardCommand;
import com.habitrpg.android.habitica.events.commands.FilterTasksByTagsCommand;
import com.habitrpg.android.habitica.events.commands.TaskCheckedCommand;
import com.habitrpg.android.habitica.helpers.TagsHelper;
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.ChecklistItem;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.raizlabs.android.dbflow.runtime.transaction.BaseTransaction;
import com.raizlabs.android.dbflow.runtime.transaction.TransactionListener;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.OrderBy;
import com.raizlabs.android.dbflow.sql.language.Select;

import java.util.List;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;


public class HabitItemRecyclerViewAdapter<THabitItem extends Task>
        extends RecyclerView.Adapter<HabitItemRecyclerViewAdapter.ViewHolder>
        implements IReceiveNewEntries {

    public interface IAdditionalEntries {
        void GetAdditionalEntries(IReceiveNewEntries callBack);
    }

    int layoutResource;
    private Class<ViewHolder<Task>> viewHolderClass;
    Integer displayedChecklist = null;
    String taskType;
    private ObservableArrayList<Task> filteredObservableContent;
    private ObservableArrayList<Task> observableContent;
    Context context;
    public int dailyResetOffset;

    static final int TYPE_HEADER = 0;
    static final int TYPE_CELL = 1;
    private RecyclerView.Adapter<ViewHolder> parentAdapter;
    private TagsHelper tagsHelper;
    private IAdditionalEntries additionalEntries;

    public HabitItemRecyclerViewAdapter(String taskType, TagsHelper tagsHelper, int layoutResource, Class<ViewHolder<Task>> viewHolderClass, Context newContext) {
        this(taskType, tagsHelper, layoutResource, viewHolderClass, newContext, null);
    }

    public HabitItemRecyclerViewAdapter(String taskType, TagsHelper tagsHelper, int layoutResource, Class<ViewHolder<Task>> viewHolderClass,
                                        Context newContext, final IAdditionalEntries additionalEntries) {
        this.setHasStableIds(true);
        this.taskType = taskType;
        this.context = newContext;
        this.tagsHelper = tagsHelper;
        this.additionalEntries = additionalEntries;
        filteredObservableContent = new ObservableArrayList<Task>();

        this.loadContent();

        this.layoutResource = layoutResource;
        this.viewHolderClass = viewHolderClass;

        EventBus.getDefault().register(this);
    }

    public void onEvent(FilterTasksByTagsCommand cmd) {
        filter();
    }

    public void onEvent(TaskCheckedCommand evnt){
        if (!taskType.equals(evnt.Task.getType()))
            return;

        if(evnt.completed && evnt.Task.getType().equals("todo")){
            // remove from the list
            observableContent.remove(evnt.Task);
        }
        this.updateTask(evnt.Task);
        filter();
    }

    public void onEvent(TaskUpdatedEvent evnt) {
        if (!taskType.equals(evnt.task.getType()))
            return;
        this.updateTask(evnt.task);
        filter();
    }

    public void onEvent(TaskCreatedEvent evnt) {
        if (!taskType.equals(evnt.task.getType()))
            return;

        observableContent.add(0, evnt.task);
        filter();
    }

    public void onEvent(TaskRemovedEvent evnt) {
        Task taskToDelete = null;

        for(Task t : observableContent) {
            if(t.getId().equals(evnt.deletedTaskId)){
                taskToDelete = t;
                break;
            }
        }

        if(taskToDelete != null) {
            observableContent.remove(taskToDelete);
            filter();
        }
    }

    private void updateTask(Task task) {
        int i;
        for(i = 0; i < this.observableContent.size(); ++i) {
            if (observableContent.get(i).getId().equals(task.getId())) {
                break;
            }
        }
        if (i < observableContent.size()) {
            observableContent.set(i, task);
        }
    }

    private void filter() {
        if (this.tagsHelper.howMany() == 0) {
            filteredObservableContent = observableContent;
        } else {
            filteredObservableContent = new ObservableArrayList<Task>();
            filteredObservableContent.addAll(this.tagsHelper.filter(observableContent));
        }

        ((Activity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();

                if (parentAdapter != null) {
                    parentAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    public void setParentAdapter(RecyclerView.Adapter<HabitItemRecyclerViewAdapter.ViewHolder> parentAdapter) {
        this.parentAdapter = parentAdapter;
    }

    @Override
    public int getItemViewType(int position) {
        switch (position) {
            default:
                return TYPE_CELL;
        }
    }

    @Override
    public long getItemId(int position) {
        Task task = filteredObservableContent.get(position);
        if (task.getId() != null && task.getId().length() == 36) {
            return UUID.fromString(task.getId()).getMostSignificantBits();
        }
        return UUID.randomUUID().getMostSignificantBits();
    }

    @Override
    public int getItemCount() {
        return filteredObservableContent.size();
    }

    @Override
    public ViewHolder<THabitItem> onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;

        switch (viewType) {
            case TYPE_CELL: {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(layoutResource, parent, false);

                switch (viewHolderClass.getSimpleName()) {
                    case "HabitViewHolder":
                        return new HabitItemRecyclerViewAdapter.HabitViewHolder(view);
                    case "DailyViewHolder":
                        return new HabitItemRecyclerViewAdapter.DailyViewHolder(view);
                    case "TodoViewHolder":
                        return new HabitItemRecyclerViewAdapter.TodoViewHolder(view);
                    case "RewardViewHolder":
                        return new HabitItemRecyclerViewAdapter.RewardViewHolder(view);
                }
            }
        }
        return null;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Task item = filteredObservableContent.get(position);

        holder.bindHolder(item, position);

        if (this.displayedChecklist != null && ChecklistedViewHolder.class.isAssignableFrom(holder.getClass())) {
            ChecklistedViewHolder checklistedHolder = (ChecklistedViewHolder) holder;
            checklistedHolder.setDisplayChecklist(this.displayedChecklist == position);
        }
    }

    // todo use debounce

    // region ViewHolders

    public abstract class ViewHolder<THabitItem extends Task> extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        protected android.content.res.Resources resources;

        public THabitItem Item;

        @InjectView(R.id.notesTextView)
        TextView notesTextView;

        public ViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(this);
            itemView.setClickable(true);

            itemView.setOnLongClickListener(this);
            itemView.setLongClickable(true);

            ButterKnife.inject(this, itemView);

            resources = itemView.getResources();
        }

        public void bindHolder(THabitItem habitItem, int position) {
            Item = habitItem;
            if (habitItem.notes == null || habitItem.notes.length() == 0) {
                notesTextView.setHeight(0);
            } else {
                notesTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }

        @Override
        public void onClick(View v) {
            if (v != itemView)
                return;

            TaskTappedEvent event = new TaskTappedEvent();
            event.Task = Item;

            EventBus.getDefault().post(event);
        }

        @Override
        public boolean onLongClick(View v) {
            TaskLongPressedEvent event = new TaskLongPressedEvent();
            event.task = Item;

            EventBus.getDefault().post(event);
            return true;
        }
    }

    public class HabitViewHolder extends ViewHolder<Task> {

        @InjectView(R.id.btnPlus)
        Button btnPlus;

        @InjectView(R.id.btnMinus)
        Button btnMinus;

        HabitItemCardBinding binding;

        public HabitViewHolder(View itemView) {
            super(itemView);

            binding = DataBindingUtil.bind(itemView);

            btnPlus.setClickable(true);
            btnPlus.setOnClickListener(this);

            btnMinus.setClickable(true);
            btnMinus.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            HabitScoreEvent event = new HabitScoreEvent();

            if (v == btnPlus) {
                event.Up = true;
                event.Habit = Item;

                EventBus.getDefault().post(event);
            } else if (v == btnMinus) {
                event.Habit = Item;

                EventBus.getDefault().post(event);
            } else super.onClick(v);
        }

        @Override
        public void bindHolder(Task habitItem, int position) {
            super.bindHolder(habitItem, position);

            binding.setHabit(habitItem);
        }
    }

    public class ChecklistedViewHolder extends ViewHolder<Task> implements CompoundButton.OnCheckedChangeListener {

        @InjectView(R.id.checkBox)
        CheckBox checkbox;

        @InjectView(R.id.checklistView)
        LinearLayout checklistView;

        @InjectView(R.id.checklistIndicatorWrapper)
        RelativeLayout checklistIndicatorWrapper;

        public Boolean displayChecklist;

        public ChecklistedViewHolder(View itemView) {
            super(itemView);
            checklistIndicatorWrapper.setOnClickListener(this);
            checkbox.setOnCheckedChangeListener(this);
        }

        @Override
        public void onClick(View v) {

            if (v == checklistIndicatorWrapper) {
                if (this.displayChecklist != null) {
                    this.setDisplayChecklist(!this.displayChecklist);
                } else {
                    this.setDisplayChecklist(true);
                }
            } else {
                super.onClick(v);
            }

        }

        @Override
        public void bindHolder(Task habitItem, int position) {
            boolean itemChanged = Item != null && !Item.getId().equals(habitItem.getId());

            super.bindHolder(habitItem, position);
            Boolean isClickable = false;
            if (habitItem.getChecklist() != null && habitItem.getChecklist().size() > 0) {
                isClickable = true;
            }
            checklistIndicatorWrapper.setClickable(isClickable);

            if(itemChanged) {
                this.setDisplayChecklist(false);
            }
        }

        public void setDisplayChecklist(Boolean displayChecklist) {
            this.displayChecklist = displayChecklist;
            //This needs to be a LinearLayout, as ListViews can not be inside other ListViews.
            if (this.checklistView != null) {
                if (this.displayChecklist && this.Item.checklist != null) {
                    LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    for (ChecklistItem item : this.Item.checklist) {
                        LinearLayout itemView = (LinearLayout) layoutInflater.inflate(R.layout.checklist_item_row, null);
                        CheckBox checkbox = (CheckBox) itemView.findViewById(R.id.checkBox);
                        TextView textView = (TextView) itemView.findViewById(R.id.checkedTextView);
                        // Populate the data into the template view using the data object
                        textView.setText(item.getText());
                        checkbox.setChecked(item.getCompleted());
                        checkbox.setOnCheckedChangeListener(this);
                        this.checklistView.addView(itemView);
                    }
                } else {
                    this.checklistView.removeAllViewsInLayout();
                }
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == checkbox) {
                if (isChecked != Item.getCompleted()) {
                    TaskCheckedCommand event = new TaskCheckedCommand();
                    event.Task = Item;
                    event.completed =  !Item.getCompleted();

                    // it needs to be changed after the event is send -> to the server
                    // maybe a refactor is needed here
                    EventBus.getDefault().post(event);
                    Item.completed =event.completed;
                    Item.save();

                }
            } else {
                View v = (View) buttonView.getParent();
                while (v.getParent() != this.checklistView) {
                    v = (View) v.getParent();
                }
                Integer position = (Integer) ((ViewGroup) v.getParent()).indexOfChild(v);
                if (isChecked != Item.checklist.get(position).getCompleted()) {
                    TaskSaveEvent event = new TaskSaveEvent();
                    Item.checklist.get(position).setCompleted(isChecked);
                    event.task = Item;
                    EventBus.getDefault().post(event);
                }
            }
        }
    }

    public class DailyViewHolder extends ChecklistedViewHolder {

        DailyItemCardBinding binding;

        public DailyViewHolder(View itemView) {
            super(itemView);

            binding = DataBindingUtil.bind(itemView);

            checkbox.setOnCheckedChangeListener(this);
            binding.setOffset(dailyResetOffset);
        }


        @Override
        public void bindHolder(Task habitItem, int position) {
            super.bindHolder(habitItem, position);

            binding.setDaily(habitItem);
        }

        @Override
        public void setDisplayChecklist(Boolean displayChecklist) {
            binding.setDisplayChecklist(displayChecklist);
            super.setDisplayChecklist(displayChecklist);
        }

    }

    public class TodoViewHolder extends ChecklistedViewHolder {

        TodoItemCardBinding binding;

        public TodoViewHolder(View itemView) {
            super(itemView);

            binding = DataBindingUtil.bind(itemView);
        }

        @Override
        public void bindHolder(Task habitItem, int position) {
            super.bindHolder(habitItem, position);

            binding.setTodo(habitItem);
        }

        @Override
        public void setDisplayChecklist(Boolean displayChecklist) {
            binding.setDisplayChecklist(displayChecklist);
            super.setDisplayChecklist(displayChecklist);
        }
    }

    public class RewardViewHolder extends ViewHolder<Task> {
        RewardItemCardBinding binding;

        @InjectView(R.id.btnReward)
        Button btnReward;

        public RewardViewHolder(View itemView) {
            super(itemView);

            binding = DataBindingUtil.bind(itemView);

            btnReward.setClickable(true);
            btnReward.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (v == btnReward) {
                BuyRewardCommand event = new BuyRewardCommand();
                event.Reward = Item;
                EventBus.getDefault().post(event);
            } else {
                if(Item.specialTag != null && Item.specialTag.equals("item")) {
                    LinearLayout contentViewForDialog = createContentViewForGearDialog();
                    MaterialDialog dialog = createGearDialog(contentViewForDialog);
                    dialog.show();
                } else {
                    TaskTappedEvent event = new TaskTappedEvent();
                    event.Task = Item;

                    EventBus.getDefault().post(event);
                }
            };
        }

        private MaterialDialog createGearDialog(LinearLayout contentViewForDialog) {
            return new MaterialDialog.Builder(context)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(MaterialDialog materialDialog, DialogAction dialogAction) {
                            BuyRewardCommand event = new BuyRewardCommand();
                            event.Reward = Item;
                            EventBus.getDefault().post(event);
                        }
                    })
                    .contentGravity(GravityEnum.CENTER)
                    .positiveColor(context.getResources().getColor(R.color.brand_200))
                    .positiveText(R.string.reward_dialog_buy)
                    .title(binding.getReward().getText())
                    .customView(contentViewForDialog, true)
                    .negativeText(R.string.reward_dialog_dismiss)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(MaterialDialog materialDialog, DialogAction dialogAction) {
                            materialDialog.dismiss();
                        }
                    }).build();
        }

        @NonNull
        private LinearLayout createContentViewForGearDialog() {
            String price = String.format("%.0f", binding.getReward().value);
            String content = binding.getReward().getNotes();

            // External ContentView
            LinearLayout contentViewLayout = new LinearLayout(context);
            contentViewLayout.setOrientation(LinearLayout.VERTICAL);

            // Gear Image
            ImageView gearImageView = new ImageView(context);
            LinearLayout.LayoutParams gearImageLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            gearImageLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
            gearImageLayoutParams.setMargins(0,0,0,20);
            gearImageView.setMinimumWidth(200);
            gearImageView.setMinimumHeight(200);
            gearImageView.setLayoutParams(gearImageLayoutParams);
            DataBindingUtils.loadImage(gearImageView, "shop_" + binding.getReward().getId());

            // Gear Description
            TextView contentTextView = new TextView(context, null);
            if(!content.isEmpty()){
                contentTextView.setText(content);
            }

            // GoldPrice View
            LinearLayout goldPriceLayout = new LinearLayout(context);
            goldPriceLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams goldPriceLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            goldPriceLayoutParams.setMargins(0, 0, 0, 16);
            goldPriceLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;

            goldPriceLayout.setOrientation(LinearLayout.HORIZONTAL);
            goldPriceLayout.setLayoutParams(goldPriceLayoutParams);
            goldPriceLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

            // Price View
            TextView priceTextView = new TextView(context);
            priceTextView.setText(price);
            priceTextView.setPadding(10, 0, 0, 0);

            ImageView gold = new ImageView(context);
            gold.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_header_gold));
            gold.setMinimumHeight(50);
            gold.setMinimumWidth(50);
            gold.setPadding(0, 0, 5, 0);

            goldPriceLayout.addView(gold);
            goldPriceLayout.addView(priceTextView);

            if(gearImageView.getDrawable()!= null){
                contentViewLayout.addView(gearImageView);
            }
            contentViewLayout.setGravity(Gravity.CENTER_VERTICAL);

            contentViewLayout.addView(goldPriceLayout);

            if(!content.isEmpty()){
                contentViewLayout.addView(contentTextView);
            }

            return contentViewLayout;
        }

        @Override
        public void bindHolder(Task reward, int position) {
            super.bindHolder(reward, position);

            binding.setReward(reward);
        }
    }

    // endregion

    public void loadContent() {
        this.loadContent(false);
    }

    public void loadContent(boolean forced) {

        if (this.observableContent == null || forced) {
            this.observableContent = new ObservableArrayList<>();
            new Select().from(Task.class)
                    .where(Condition.column("type").eq(this.taskType))
                    .and(Condition.CombinedCondition
                                    .begin(Condition.column("completed").eq(false))
                                    .or(Condition.column("type").eq("daily"))
                    )
                    .orderBy(OrderBy.columns("position", "dateCreated").descending())
                    .async()
                    .queryList(taskTransactionListener);

        }
    }

    private TransactionListener<List<Task>> taskTransactionListener = new TransactionListener<List<Task>>() {
        @Override
        public void onResultReceived(List<Task> tasks) {
            observableContent.clear();
            observableContent.addAll(tasks);
            if (additionalEntries != null) {
                additionalEntries.GetAdditionalEntries(HabitItemRecyclerViewAdapter.this);
            }
            filter();
        }

        @Override
        public boolean onReady(BaseTransaction<List<Task>> transaction) {
            return true;
        }

        @Override
        public boolean hasResult(BaseTransaction<List<Task>> transaction, List<Task> result) {
            return true;
        }
    };

    @Override
    public void GotAdditionalItems(List<Task> items) {
        this.observableContent.addAll(items);

        if (parentAdapter != null) {
            parentAdapter.notifyDataSetChanged();
        } else {
            notifyDataSetChanged();
        }
    }
}
