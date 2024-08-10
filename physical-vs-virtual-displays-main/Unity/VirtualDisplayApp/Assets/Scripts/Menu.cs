using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

public class Menu : MonoBehaviour
{
    public Button Surface_Hub_Scene_Button;
    public Button Display_Wall_Scene_Button;
    public Button Custom_Scene_Button;
    public Button QuitButton;

    void Start()
    {
        // Add listeners to buttons
        Surface_Hub_Scene_Button.onClick.AddListener(() => LoadScene("Surface Hub"));
        Display_Wall_Scene_Button.onClick.AddListener(() => LoadScene("Display Wall"));
        Custom_Scene_Button.onClick.AddListener(() => LoadScene("Custom"));
        QuitButton.onClick.AddListener(QuitGame);
    }

    private void LoadScene(string sceneName)
    {
        SceneManager.LoadScene(sceneName);
    }

    private void QuitGame()
    {
        // Clean up resources before quitting
        QuitButton.onClick.RemoveAllListeners(); // Remove all listeners to prevent memory leaks
        SceneManager.LoadScene("Main Menu"); // Load a default scene before quitting
        Application.Quit(); // Quit the application
    }

    private void OnDestroy()
    {
        // Ensure listeners are removed when the script is destroyed
        Surface_Hub_Scene_Button.onClick.RemoveAllListeners();
        Display_Wall_Scene_Button.onClick.RemoveAllListeners();
        Custom_Scene_Button.onClick.RemoveAllListeners();
        QuitButton.onClick.RemoveAllListeners();
    }
}
