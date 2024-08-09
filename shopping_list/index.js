import { initializeApp } from "https://www.gstatic.com/firebasejs/9.15.0/firebase-app.js"
import { getDatabase, ref, push, onValue, remove } from "https://www.gstatic.com/firebasejs/9.15.0/firebase-database.js"

const appSettings = {
    databaseURL: "https://shoppinglist-485f8-default-rtdb.europe-west1.firebasedatabase.app/"
}

const app =  initializeApp(appSettings)
const database = getDatabase(app)
const shoppingListInDB = ref(database, "shoppingList")


let input = document.getElementById("input-field")
let addBtn =  document.getElementById("add-button")
let shoppingListEl = document.getElementById("shopping-list")

addBtn.addEventListener("click", function(){
    let inputValue = input.value
    push(shoppingListInDB, inputValue)
    
    reset()
})

onValue(shoppingListInDB, function(snapshot){
    
    
    if (snapshot.exists()){
        let itemsArray = Object.entries(snapshot.val())
        clearShoppingListEl()
        for(let i = 0; i < itemsArray.length; i++){  
            let item =  itemsArray[i]
            let currentItemID = item[0]
            let currentItemValue = item[1]
            add(item)
        }
    }else{
        shoppingListEl.innerHTML = "No items here...yet"
    }
    
    
})

function clearShoppingListEl(){
    shoppingListEl.innerHTML = ""
}

function reset(){
    input.value = ""
}

function add(item){
    let itemID = item[0]
    let itemValue = item[1]
    let newEl = document.createElement("li")
    
    newEl.textContent = itemValue
    newEl.addEventListener("dblclick", function(){
        let exactLocationInDB = ref(database, `shoppingList/${itemID}`)
        remove(exactLocationInDB)
    })
    shoppingListEl.append(newEl)
}
