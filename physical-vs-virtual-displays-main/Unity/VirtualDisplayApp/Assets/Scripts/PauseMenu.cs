using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

public class PauseMenu : MonoBehaviour
{
    public Button PauseButton;
    public Button ResumeButton;
    public Button MenuButton;
    public Button QuitButton;

    public GameObject PauseMenuUI;
    private GameObject mainCamera;

    private void Start()
    {
        PauseButton.onClick.AddListener(Pause);
        ResumeButton.onClick.AddListener(Resume);
        MenuButton.onClick.AddListener(LoadMainMenu);
        QuitButton.onClick.AddListener(QuitGame);

        mainCamera = GameObject.FindGameObjectWithTag("MainCamera");
    }

    private void OnDestroy()
    {
        // Remove all listeners to prevent memory leaks
        PauseButton.onClick.RemoveAllListeners();
        ResumeButton.onClick.RemoveAllListeners();
        MenuButton.onClick.RemoveAllListeners();
        QuitButton.onClick.RemoveAllListeners();
    }

    private void Resume()
    {
        PauseMenuUI.SetActive(false);
    }

    private void Pause()
    {
        PauseMenuUI.SetActive(true);
        if (mainCamera != null)
        {
            Transform cameraTransform = mainCamera.transform;
            cameraTransform.position = Vector3.zero;
            cameraTransform.rotation = Quaternion.identity;
        }
    }

    private void LoadMainMenu()
    {
        SceneManager.LoadScene("Menu");
    }

    private void QuitGame()
    {
        Application.Quit();
    }
}
