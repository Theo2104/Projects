using Microsoft.MixedReality.Toolkit.UI;
using System;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.UIElements;
using Toggle = UnityEngine.UI.Toggle;

public class EditMode : MonoBehaviour
{
    public Toggle toggle;
    public GameObject canvasPrefab;
    private RectTransform rectTransform;

    void Start()
    {
        toggle.onValueChanged.AddListener(delegate { edit(); });
    }

    public void edit()
    {
        if (toggle.isOn)
        {
            // Find all objects with Tag "Moveable"
            GameObject[] moveables = GameObject.FindGameObjectsWithTag("Moveable");

            // Loop through all found objects
            foreach (GameObject moveable in moveables)
            {
                // Instanziere das Canvas Prefab
                GameObject canvasInstance = Instantiate(canvasPrefab);
                Vector3 scale = moveable.transform.localScale;
                rectTransform = canvasInstance.GetComponent<RectTransform>();
                canvasInstance.transform.position = new Vector3(moveable.transform.position.x, moveable.transform.position.y, moveable.transform.position.z);
                rectTransform.sizeDelta = new Vector2(1f, 1f);
                rectTransform.localScale = new Vector3(scale.x + 0.4f, scale.y + 0.2f, scale.z);
                rectTransform.rotation = moveable.transform.rotation;
                moveable.transform.SetParent(canvasInstance.transform);
                moveable.transform.localPosition = new Vector3(0, 0, -1);
            }
        }
        else
        {
            GameObject[] objectsToRemove = GameObject.FindGameObjectsWithTag("Remove");

            // Loop through all found objects
            foreach (GameObject objectToRemove in objectsToRemove)
            {
                if (objectToRemove != null)
                {
                    // Trenne alle Kinder des Objekts
                    objectToRemove.transform.DetachChildren();

                    // Entferne das Objekt
                    Destroy(objectToRemove);
                }
                else
                {
                    Debug.LogWarning("Das zu entfernende Objekt ist null.");
                }
            }
        }
    }
}
