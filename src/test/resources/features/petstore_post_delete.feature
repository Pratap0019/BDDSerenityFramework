@testingAPI
Feature: Create and Delete Pet

  Scenario: Create a new pet
    When I create a pet with ID 99999 and name "Fluffy"
    Then the pet should be created successfully

  Scenario: Delete the pet
    When I delete the pet with ID 99999
    Then the pet should be deleted successfully
