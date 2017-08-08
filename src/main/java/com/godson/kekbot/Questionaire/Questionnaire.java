package com.godson.kekbot.Questionaire;

import com.darichey.discord.api.CommandContext;
import com.godson.kekbot.EventWaiter.EventWaiter;
import com.godson.kekbot.KekBot;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.requests.RestAction;

import java.io.StringBufferInputStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Questionnaire {
    private List<Question> questions = new ArrayList<>();
    private EventWaiter waiter = KekBot.waiter;
    private List<Object> answers = new ArrayList<>();
    private Map<Question, List<String>> choices = new HashMap<>();
    private boolean skipQuestionMessage = false;
    private boolean skipOnRepeat = false;
    private boolean skipErrorOnRepeat = false;
    private boolean customErrorMessageEnabled = false;
    private String customErrorMessage;

    //Guild Info:
    private Guild guild;
    private TextChannel channel;
    private User user;
    private CommandContext context;

    public ArrayList<String> yesChoices = new ArrayList<>(Arrays.asList("yes", "y"));
    public ArrayList<String> noChoices = new ArrayList<>(Arrays.asList("no", "n"));
    public ArrayList<String> yesOrNoChoices = (ArrayList<String>) Stream.concat(yesChoices.stream(), noChoices.stream())
            .collect(Collectors.toList());

    private Consumer<Results> results = results -> {};
    private Consumer<Object> canceled = unused -> {};

    public Questionnaire(CommandContext context) {
        this(context.getGuild(), context.getTextChannel(), context.getAuthor());
        this.context = context;
    }

    public Questionnaire(GuildMessageReceivedEvent event) {
        this(event.getGuild(), event.getChannel(), event.getAuthor());
    }

    public Questionnaire(Results results) {
        this(results.getGuild(), results.getChannel(), results.getUser());
        if (results.context != null) this.context = results.context;
    }

    public Questionnaire(Guild guild, TextChannel channel, User user) {
        this.guild = guild;
        this.channel = channel;
        this.user = user;
    }

    public Questionnaire withoutRepeats() {
        this.skipOnRepeat = true;
        return this;
    }

    public Questionnaire withoutErrorMessage() {
        this.skipErrorOnRepeat = true;
        return this;
    }

    public Questionnaire withCustomErrorMessage(String customErrorMessage) {
        customErrorMessageEnabled = true;
        this.customErrorMessage = customErrorMessage;
        return this;
    }

    public Questionnaire addQuestion(String message, QuestionType type) {
        //Allows addition of other "Types" that require more params.
        if (type.equals(QuestionType.CHOICE_STRING)) {
            String method = "";
            //Add any new "types" to this switch, as well as the method used to create questions of that type.
            switch (type) {
                case CHOICE_STRING:
                case CHOICE_REST_STRING:
                    method = "addChoiceQuestion()";
                    break;
                case YES_NO_STRING:
                    method = "addYesNoQuestion()";
                    break;
            }
            throw new IllegalArgumentException("You are not allowed to set this type of question. (Please use " + method + " to use this type.");
        }
        questions.add(new Question(message).setType(type));
        return this;
    }

    public Questionnaire addChoiceQuestion(String message, String... choices) {
        Question question = new Question(message).setType(QuestionType.CHOICE_STRING);
        questions.add(question);
        this.choices.put(question, Arrays.asList(choices));
        return this;
    }

    public Questionnaire addChoiceQuestion(String message, boolean acceptRest, String... choices) {
        Question question = new Question(message)
                .setType(acceptRest ? QuestionType.CHOICE_REST_STRING : QuestionType.CHOICE_STRING);
        questions.add(question);
        this.choices.put(question, Arrays.asList(choices));
        return this;
    }

    public Questionnaire addYesNoQuestion(String message) {
        Question question = new Question(message).setType(QuestionType.YES_NO_STRING);
        questions.add(question);
        this.choices.put(question, this.yesOrNoChoices);
        return this;
    }

    public void execute(Consumer<Results> results) {
        if (context != null) context.getRegistry().disableUserInGuild(guild, user);
        this.results = results;
        execute(0);
    }

    public void execute(Consumer<Results> results, Consumer<Object> canceled) {
        if (context != null) context.getRegistry().disableUserInGuild(guild, user);
        this.results = results;
        this.canceled = canceled;
        execute(0);
    }

    public void executeNow(String message, Consumer<Results> results) {
        if (context != null) context.getRegistry().disableUserInGuild(guild, user);
        this.results = results;
        executeNow(0, message, channel);
    }

    public void executeNow(String message, Consumer<Results> results, Consumer<Object> canceled) {
        if (context != null) context.getRegistry().disableUserInGuild(guild, user);
        this.results = results;
        this.canceled = canceled;
        executeNow(0, message, channel);
    }

    private void execute(int i) {
        Question question = questions.get(i);
        if (!skipQuestionMessage) channel.sendMessage(question.getMessage()).queue();
        waiter.waitForEvent(GuildMessageReceivedEvent.class, e -> e.getAuthor().equals(user) && e.getChannel().equals(channel), e -> {
            this.executeNow(question, e.getMessage().getContent(), e.getChannel(), i);
        });
    }

    private void executeNow(int i, String message, TextChannel channel) {
        Question question = questions.get(i);
        executeNow(questions.get(i), message, channel, i);
    }

    private void executeNow(Question question, String message, TextChannel eventChannel, int i) {
        RestAction<Message> errorMessage = eventChannel.sendMessage((!customErrorMessageEnabled ? "I'm sorry, I didn't quite catch that, let's try that again..." : customErrorMessage));
        if (message.equalsIgnoreCase("cancel")) {
            eventChannel.sendMessage("Cancelled.").queue();
            if (context != null) context.getRegistry().enableUserInGuild(guild, user);
            finish(false);
        } else {
            switch (question.getType()) {
                case STRING:
                    answers.add(message);
                    break;
                case INT:
                    try {
                        answers.add(Integer.valueOf(message));
                    } catch (NumberFormatException e1) {
                        if (skipOnRepeat) skipQuestionMessage = true;
                        errorMessage.queue();
                        execute(i);
                        return;
                    }
                case CHOICE_STRING:
                    Optional<String> choice = choices.get(question).stream().filter(c -> c.equalsIgnoreCase(message)).findFirst();
                    if (!choice.isPresent()) {
                        if (skipOnRepeat) skipQuestionMessage = true;
                        if (!skipErrorOnRepeat) errorMessage.queue();
                        execute(i);
                        return;
                    } else {
                        answers.add(choice.get());
                    }
                    break;
                case CHOICE_REST_STRING:
                    Optional<String> choiceRest = choices.get(question).stream().filter(message::startsWith).findFirst();
                    if (!choiceRest.isPresent()) {
                        if (skipOnRepeat) skipQuestionMessage = true;
                        if (!skipErrorOnRepeat) errorMessage.queue();
                        execute(i);
                        return;
                    } else {
                        answers.add(choiceRest.get());
                    }
                    break;
                case YES_NO_STRING:
                    Optional<String> yesNoChoice = choices.get(question).stream().filter(c -> c.equalsIgnoreCase(message)).findFirst();
                    if (!yesNoChoice.isPresent()) {
                        if (skipOnRepeat) skipQuestionMessage = true;
                        errorMessage.queue();
                        execute(i);
                        return;
                    } else {
                        answers.add(yesNoChoice.get());
                    }
            }
            if (i + 1 != questions.size()) {
                if (skipOnRepeat && skipQuestionMessage) skipQuestionMessage = false;
                execute(i + 1);
            } else {
                if (context != null) context.getRegistry().enableUserInGuild(guild, user);
                finish(true);
            }
        }
    }

    private void finish(boolean success) {
        if (this.answers.isEmpty()) throw new IllegalStateException("Questionnaire must have an answer");
        if (success) results.accept(new Results(this));
        else canceled.accept(null);
    }

    public class Results {
        private Questionnaire questionnaire;
        private List<Object> answers;
        private Guild guild;
        private TextChannel channel;
        private User user;
        private CommandContext context;


        Results(Questionnaire questionnaire) {
            this.questionnaire = questionnaire;
            this.answers = questionnaire.answers;
            this.guild = questionnaire.guild;
            this.channel = questionnaire.channel;
            this.user = questionnaire.user;
            if (questionnaire.context != null) this.context = questionnaire.context;
        }

        public Questionnaire getQuestionnaire() {
            return questionnaire;
        }

        public Object getAnswer(int i) {
            return answers.get(i);
        }

        public List<Object> getAnswers() {
            return answers;
        }

        public Guild getGuild() {
            return guild;
        }

        public TextChannel getChannel() {
            return channel;
        }

        public User getUser() {
            return user;
        }

        public void reExecute() {
            questionnaire.answers.clear();
            questionnaire.execute(results);
        }

        public void reExecuteWithoutMessage() {
            questionnaire.answers.clear();
            questionnaire.skipQuestionMessage = true;
            questionnaire.execute(results);
        }
    }

}
