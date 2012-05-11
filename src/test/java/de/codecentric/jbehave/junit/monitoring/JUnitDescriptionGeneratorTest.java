package de.codecentric.jbehave.junit.monitoring;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.everyItem;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.StepCandidate;
import org.jbehave.core.steps.Steps;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.codecentric.jbehave.junit.monitoring.JUnitDescriptionGenerator;

public class JUnitDescriptionGeneratorTest {

	private static final String DEFAULT_STORY_NAME = "Default Story Name";
	private static final String DEFAULT_SCENARIO_TITLE = "Default Scenario Title";
	@Mock
	StepCandidate stepCandidate;
	@Mock
	Steps steps;
	@Mock
	Story story;
	@Mock 
	Scenario scenario;
	@Mock
	GivenStories givenStories;

	private JUnitDescriptionGenerator generator;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		when(steps.listCandidates()).thenReturn(Arrays.asList(new StepCandidate[] {stepCandidate}));
		when(stepCandidate.matches(anyString())).thenReturn(true);
		when(stepCandidate.getStepsInstance()).thenReturn(new Object());
		when(story.getName()).thenReturn(DEFAULT_STORY_NAME);
		when(scenario.getTitle()).thenReturn(DEFAULT_SCENARIO_TITLE);
		when(givenStories.getPaths()).thenReturn(Collections.<String>emptyList());
		when(scenario.getGivenStories()).thenReturn(givenStories);
		generator = new JUnitDescriptionGenerator(Arrays.asList(new CandidateSteps[] {steps}));
	}

	@Test
	public void shouldNotCountIgnorables() {
		when(scenario.getSteps()).thenReturn(Arrays.asList("Step1", "!-- ignore me"));
		when(stepCandidate.matches(anyString())).thenReturn(false);
		when(stepCandidate.matches("Step1")).thenReturn(true);
		generator.createDescriptionFrom(scenario);
		assertThat(generator.getTestCases(), is(1));
	}
	
	@Test
	public void shouldGenerateDescriptionForTopLevelScenario() {
		when(scenario.getTitle()).thenReturn("MyTitle");
		Description description = generator.createDescriptionFrom(scenario);
		assertThat(description, equalTo(Description.createSuiteDescription("Scenario: MyTitle")));
		assertThat(generator.getTestCases(), is(0));
	}

	@Test
	public void shouldGenerateDescriptionForStep() {
		addStepToScenario();
		Description description = generator.createDescriptionFrom(scenario);
		assertThat(description.getChildren(), hasItem(step1Description()));
		assertThat(generator.getTestCases(), is(1));
	}
	
	@Test
	public void shouldGenerateDescriptionForTabularParameterStep() {
		when(scenario.getSteps()).thenReturn(Arrays.asList("StepWithTableParam:\n|Head|\n|Value|"));
		Description description = generator.createDescriptionFrom(scenario);
		assertThat(description.getChildren(), hasItem(stepWithTableDescription()));
		assertThat(generator.getTestCases(), is(1));
	}

	private void addStepToScenario() {
		when(scenario.getSteps()).thenReturn(Arrays.asList("Step1"));
	}

	private Description step1Description() {
		return Description.createTestDescription(Object.class, "Step1");
	}
	
	private Description stepWithTableDescription() {
		return Description.createTestDescription(Object.class, "StepWithTableParam:");
	}

	@Test
	public void shouldGenerateDescriptionForStory() {
		Description description = generator.createDescriptionFrom(story);
		assertThat(description, is(Description
				.createSuiteDescription(DEFAULT_STORY_NAME)));
	}

	@Test
	public void shouldGenerateDescriptionForScenarioChildOfStory() {
		when(story.getScenarios()).thenReturn(Arrays.asList(new Scenario[] {scenario}));
		Description description = generator.createDescriptionFrom(story);
		assertThat(description.getChildren(), hasItem(Description.createSuiteDescription("Scenario: " + DEFAULT_SCENARIO_TITLE)));
	}
	
	@Test
	public void shouldStripLinebreaksFromScenarioDescriptions() {
		Scenario scenario = mock(Scenario.class);
		when(story.getScenarios()).thenReturn(Arrays.asList(new Scenario[] {scenario}));
		when(scenario.getGivenStories()).thenReturn(givenStories);

		when(scenario.getTitle()).thenReturn("Scenario with\nNewline");
		Description description = generator.createDescriptionFrom(story);
		assertThat(description.getChildren().get(0).getDisplayName(), not(containsString("\n")));
	}
	
	@Test
	public void shouldStripCarriageReturnsFromScenarioDescriptions() {
		Scenario scenario = mock(Scenario.class);
		when(story.getScenarios()).thenReturn(Arrays.asList(new Scenario[] {scenario}));
		when(scenario.getGivenStories()).thenReturn(givenStories);

		when(scenario.getTitle()).thenReturn("Scenario with\rCarriage Return");
		Description description = generator.createDescriptionFrom(story);
		assertThat(description.getChildren().get(0).getDisplayName(), not(containsString("\r")));
	}
	
	

	@Test
	public void shouldCopeWithSeeminglyDuplicateSteps() throws Exception {
		when(scenario.getSteps()).thenReturn(Arrays.asList(new String[] {"Step1", "Step1"}));
		Description description = generator.createDescriptionFrom(scenario);
		assertThat(description.getChildren(), everyItem(Matchers.<Description>hasProperty("displayName", startsWith("Step1"))));
		assertThat(description.getChildren().size(), is(2));
		assertThat(description.getChildren(), allChildrenHaveUniqueDisplayNames());
		assertThat(generator.getTestCases(), is(2));
	}

	@Test
	public void shouldCopeWithDuplicateGivenStories() throws Exception {
		when(story.getScenarios()).thenReturn(Arrays.asList(new Scenario[] {scenario, scenario}));
		when(givenStories.getPaths()).thenReturn(Arrays.asList("/some/path/to/GivenStory.story"));
		Description description = generator.createDescriptionFrom(story);
		Description firstScenario = description.getChildren().get(0);
		Description secondScenario = description.getChildren().get(1);
		assertThat(firstScenario.getChildren().get(0).getDisplayName(), is(not(secondScenario.getChildren().get(0).getDisplayName())));
	}
	
	@Test
	public void shouldGenerateDescriptionForGivenStories() {
		when(givenStories.getPaths()).thenReturn(Arrays.asList("/some/path/to/GivenStory.story"));
		Description description = generator.createDescriptionFrom(scenario);
		assertThat(description.getChildren().get(0), hasProperty("displayName", is("GivenStory.story")));
		assertThat(generator.getTestCases(), is(1));
	}
	
	@Test
	public void shouldGenerateDescriptionForExampleTablesOnScenario() {
		addStepToScenario();
		int NUM_ROWS = 2;
		Map<String, String> row = addExamplesTableToScenario(NUM_ROWS);
		
		Description description = generator.createDescriptionFrom(scenario);
		
		assertThat(description.getChildren().size(), is(NUM_ROWS));
		for (Description exampleDescription : description.getChildren()) {
			assertThat(exampleDescription.getChildren(), hasItem(Matchers.<Description>hasProperty("displayName", startsWith("Step1"))));
			assertThat(exampleDescription, hasProperty("displayName", startsWith("Example: " + row)));
		}
		
	}

	private Map<String, String> addExamplesTableToScenario(int NUM_ROWS) {
		ExamplesTable examplesTable = mock(ExamplesTable.class);
		when(examplesTable.getRowCount()).thenReturn(NUM_ROWS);
		Map<String, String> row = new TreeMap<String, String>();
		for(int i=1; i<=NUM_ROWS; i++) {
			row.put("key"+i, "value"+i);
		}
		when(examplesTable.getRow(anyInt())).thenReturn(row);
		when(scenario.getExamplesTable()).thenReturn(examplesTable);
		return row;
	}
	
	private Matcher<List<Description>> allChildrenHaveUniqueDisplayNames() {
		return new BaseMatcher<List<Description>>() {

			private List<Description> descriptions;

			@SuppressWarnings("unchecked")
			public boolean matches(Object descriptions) {
				this.descriptions = (List<Description>) descriptions;
				Set<String> displayNames = new HashSet<String>();
				for (Description child : this.descriptions) {
					displayNames.add(child.getDisplayName());
				}
				return displayNames.size() == this.descriptions
						.size();
			}

			public void describeTo(org.hamcrest.Description description) {
				description
						.appendText("Children of description do not have unique display names");
				for (Description child : descriptions) {
					description.appendText(child.getDisplayName());
				}
			}

		};
	}
}