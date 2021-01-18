package com.xatkit.core.recognition.nlpjs.mapper;

import com.xatkit.core.recognition.nlpjs.NlpjsConfiguration;
import com.xatkit.core.recognition.nlpjs.NlpjsHelper;
import com.xatkit.core.recognition.nlpjs.model.BetweenCondition;
import com.xatkit.core.recognition.nlpjs.model.Entity;
import com.xatkit.core.recognition.nlpjs.model.Intent;
import com.xatkit.core.recognition.nlpjs.model.IntentExample;
import com.xatkit.core.recognition.nlpjs.model.IntentParameter;
import com.xatkit.intent.BaseEntityDefinition;
import com.xatkit.intent.ContextParameter;
import com.xatkit.intent.EntityType;
import com.xatkit.intent.IntentDefinition;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.nonNull;

public class NlpjsIntentMapper {

    private NlpjsConfiguration configuration;

    private NlpjsEntityReferenceMapper nlpjsEntityReferenceMapper;

    public NlpjsIntentMapper(@NonNull NlpjsConfiguration configuration,
                             @NonNull NlpjsEntityReferenceMapper nlpjsEntityReferenceMapper) {
        this.configuration = configuration;
        this.nlpjsEntityReferenceMapper = nlpjsEntityReferenceMapper;

    }

    public Intent mapIntentDefinition(@NonNull IntentDefinition intentDefinition, List<Entity> anyEntitiesCollector) {
        checkNotNull(intentDefinition.getName(), "Cannot map the %s with the provided name %s",
                IntentDefinition.class.getSimpleName(), intentDefinition.getName());
        Intent.Builder builder = Intent.newBuilder()
                .intentName(intentDefinition.getName());
        Map<String, IntentParameter> intentParametersMap = new HashMap<>();
        Map<String, Entity> anyEntitiesMap = new HashMap<>();
        List<IntentExample> intentExamples = createIntentExamples(intentDefinition, intentParametersMap, anyEntitiesMap);
        anyEntitiesCollector.addAll(anyEntitiesMap.values());
        builder.examples(intentExamples);
        builder.parameters(new ArrayList<>(intentParametersMap.values()));
        return builder.build();
    }

    private List<IntentExample> createIntentExamples(@NonNull IntentDefinition intentDefinition, @NonNull Map<String, IntentParameter> intentParametersMap, Map<String, Entity> anyEntitiesMap) {
        List<IntentExample> intentExamples = new ArrayList<>();
        for (String trainingSentence : intentDefinition.getTrainingSentences()) {
            intentExamples.add(createIntentExample(intentDefinition, trainingSentence, intentParametersMap, anyEntitiesMap));
        }
        return intentExamples;
    }

    private IntentExample createIntentExample(@NonNull IntentDefinition intentDefinition, @NonNull String trainingSentence, @NonNull Map<String, IntentParameter> intentParametersMap, Map<String, Entity> anyEntitiesMap) {
        if (intentDefinition.getParameters().isEmpty()) {
            return new IntentExample(trainingSentence);
        } else {
            String preparedTrainingSentence = trainingSentence;
            for (ContextParameter parameter : intentDefinition.getParameters()) {
                if (preparedTrainingSentence.contains(parameter.getTextFragments().get(0))) {
                    preparedTrainingSentence = preparedTrainingSentence.replace(parameter.getTextFragments().get(0), "#"
                            + parameter.getTextFragments().get(0) + "#");
                }
            }

            String[] splitTrainingSentence = preparedTrainingSentence.split("#");
            StringBuilder intentExampleBuilder = new StringBuilder();
            for (String sentencePart : splitTrainingSentence) {
                boolean isParameter = false;
                    for (ContextParameter parameter : intentDefinition.getParameters()) {
                        if (sentencePart.equals(parameter.getTextFragments().get(0))) {
                            checkNotNull(parameter.getName(), "Cannot build the training sentence \"%s\", the " +
                                            "parameter for the fragment \"%s\" does not define a name",
                                    trainingSentence, parameter.getTextFragments().get(0));
                            checkNotNull(parameter.getEntity(), "Cannot build the training sentence \"%s\", the " +
                                            "parameter for the fragment \"%s\" does not define an entity",
                                    trainingSentence, parameter.getTextFragments().get(0));
                            isParameter = true;
                            String nlpEntity = null;
                            boolean isAny = false;

                            /*
                             * Gracefully degrades the provided parameter type to "any" if the base entity it refers to
                             * is not supported. This allows NLP.js to match the entity, but it is more permissive
                             * than a correct implementation of it (it will basically match any value for the
                             * parameter).
                             * This pre-processing is done before any-specific entity configuration, allowing to
                             * reuse before/after extraction rules or regex, depending on the nature of the training
                             * sentence.
                             */
                            if (nlpjsEntityReferenceMapper.getMappingFor(parameter.getEntity().getReferredEntity()).equals("none")) {
                                if (parameter.getEntity().getReferredEntity() instanceof BaseEntityDefinition) {
                                    BaseEntityDefinition baseEntityDefinition =
                                            (BaseEntityDefinition) parameter.getEntity().getReferredEntity();
                                    baseEntityDefinition.setEntityType(EntityType.ANY);
                                }
                            }

                            if (parameter.getEntity().getReferredEntity() instanceof BaseEntityDefinition &&
                                    ((BaseEntityDefinition) parameter.getEntity().getReferredEntity()).getEntityType().equals(EntityType.ANY)) {
                                int textFragmentIndexStart =
                                        trainingSentence.indexOf(parameter.getTextFragments().get(0));
                                int textFragmentIndexEnd =
                                        textFragmentIndexStart + parameter.getTextFragments().get(0).length();
                                String[] preParameterArray = null;
                                String[] postParameterArray = null;
                                if (textFragmentIndexStart != 0) {
                                    preParameterArray  = trainingSentence.substring(0, textFragmentIndexStart).split(" ");
                                }
                                if (textFragmentIndexEnd != trainingSentence.length()) {
                                    postParameterArray = trainingSentence.substring(textFragmentIndexEnd).split(" ");
                                }

                                String beforeLast = null;
                                String afterLast = null;

                                if (nonNull(preParameterArray) && preParameterArray.length > 0) {
                                    afterLast = preParameterArray[preParameterArray.length - 1];
                                }
                                if (nonNull(postParameterArray) && postParameterArray.length > 0) {
                                    beforeLast =
                                            Arrays.stream(postParameterArray).filter(v -> !v.equals("")).findFirst().orElse(null);
                                    if(nonNull(beforeLast) && beforeLast.equals("?")) {
                                        beforeLast = "\\?";
                                    }
                                }

                                if (nonNull(beforeLast) || nonNull(afterLast)) {
                                    nlpEntity = intentDefinition.getName() + parameter.getName() + "Any";
                                    isAny = true;
                                    if (anyEntitiesMap.containsKey(nlpEntity)) {
                                        if (nonNull(beforeLast) && nonNull(afterLast)) {
                                            Entity entity = anyEntitiesMap.get(nlpEntity);
                                            if(entity.getBetween() == null) {
                                                BetweenCondition betweenCondition = new BetweenCondition();
                                                entity.setBetween(betweenCondition);
                                            }
                                            entity.getBetween().getLeft().add(afterLast);
                                            entity.getBetween().getRight().add(beforeLast);
                                        } else if (nonNull(beforeLast)) {
                                            anyEntitiesMap.get(nlpEntity).getBeforeLast().add(beforeLast);
                                        } else {
                                            anyEntitiesMap.get(nlpEntity).getAfterLast().add(afterLast);
                                        }
                                    } else {
                                        Entity.Builder builder = Entity.newBuilder();
                                        builder.entityName(nlpEntity);
                                        builder.type(com.xatkit.core.recognition.nlpjs.model.EntityType.TRIM);
                                        if (nonNull(beforeLast) && nonNull(afterLast)) {
                                            BetweenCondition betweenCondition = new BetweenCondition();
                                            betweenCondition.getRight().add(beforeLast);
                                            betweenCondition.getLeft().add(afterLast);
                                            builder.between(betweenCondition);
                                        } else if (nonNull(beforeLast)) {
                                            builder.addBeforeLast(beforeLast);
                                        } else {
                                            builder.addAfterLast(afterLast);
                                        }
                                        anyEntitiesMap.put(nlpEntity,builder.build());
                                    }
                                } else {
                                    /*
                                     * We didn't find anything before or after the text fragment mapped to an "any"
                                     * entity. This means that the entire training sentence is mapped to the entity,
                                     * and we map it to a regex matching anything.
                                     */
                                    nlpEntity = intentDefinition.getName() + parameter.getName() + "Any";
                                    Entity entity = Entity.newBuilder()
                                            .entityName(nlpEntity)
                                            .type(com.xatkit.core.recognition.nlpjs.model.EntityType.REGEX)
                                            /*
                                             * Note that we use + here instead of *: otherwise the NLP.js server goes on
                                             * an infinite recursion trying to match it.
                                             */
                                            .addRegex("/.+/")
                                            .build();
                                    anyEntitiesMap.put(nlpEntity, entity);
                                    isAny = true;
                                }
                            } else {
                                nlpEntity = nlpjsEntityReferenceMapper.getMappingFor(parameter.getEntity()
                                        .getReferredEntity());

                            }
                            StringBuilder nlpjsIntentParameterBuilder = new StringBuilder().append(nlpEntity);
                            if (!isAny && NlpjsHelper.getEntityCount(parameter.getEntity().getReferredEntity(),
                                    intentDefinition) > 1) {
                                nlpjsIntentParameterBuilder.append("_").append(NlpjsHelper.getEntityTypeIndex(parameter.getTextFragments().get(0),
                                        parameter.getEntity().getReferredEntity(), intentDefinition));
                            }
                            String nlpjsIntentParameter = nlpjsIntentParameterBuilder.toString();
                            intentExampleBuilder.append("%").append(nlpjsIntentParameter).append("%");
                            if (!intentParametersMap.containsKey(parameter.getName())) {
                                IntentParameter intentParameter = new IntentParameter();
                                intentParameter.setSlot(nlpjsIntentParameter);
                                intentParametersMap.put(parameter.getName(), intentParameter);
                            }
                        }
                }
                if (!isParameter) {
                    intentExampleBuilder.append(sentencePart);
                }
            }
            return new IntentExample(intentExampleBuilder.toString());

        }
    }


}
