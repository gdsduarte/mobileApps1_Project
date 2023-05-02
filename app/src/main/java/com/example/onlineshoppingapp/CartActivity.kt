package com.example.onlineshoppingapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.onlineshoppingapp.adapters.CartAdapter
import com.example.onlineshoppingapp.helpers.AppCart
import com.example.onlineshoppingapp.helpers.CartUtils
import com.example.onlineshoppingapp.helpers.FakeStoreApiClient
import com.example.onlineshoppingapp.helpers.SharedPreferencesHelper
import com.example.onlineshoppingapp.models.Cart
import com.example.onlineshoppingapp.models.CartItem
import com.example.onlineshoppingapp.models.Product
import com.example.onlineshoppingapp.models.Rating
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CartActivity : AppCompatActivity() {

    private lateinit var cart: Cart
    private var carts: MutableList<Cart>? = null
    private val sharedPreferencesHelper by lazy { SharedPreferencesHelper.getInstance(this) }
    private lateinit var fakeStoreApiClient: FakeStoreApiClient
    private lateinit var selectedProduct: Product
    private var selectedQuantity: Int = 0
    private var totalCost: Double = 0.0
    companion object {
        const val CHANNEL_ID = "sample_channel_id"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        fakeStoreApiClient = FakeStoreApiClient()
        createNotificationChannel()

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNavView)

        bottomNavigationView.selectedItemId = R.id.cart
        bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(applicationContext, HomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.shop -> {
                    startActivity(Intent(applicationContext, ProductActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.cart -> true
                R.id.orders -> {
                    startActivity(Intent(applicationContext, OrderActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.profile -> {
                    startActivity(Intent(applicationContext, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }

        AppCart.cart = sharedPreferencesHelper.getCart() ?: Cart(id = 0, userId = 0, date = "", products = mutableListOf())

        // Get the cart object from the intent
        cart = AppCart.cart

        // Get product, quantity, and price from the Intent
        intent.extras?.let {
            @Suppress("DEPRECATION")
            selectedProduct = it.getSerializable("selectedProduct") as? Product ?: Product(
                id = 0,
                title = "",
                price = 0.0,
                description = "",
                category = "",
                image = "",
                rating = Rating(0.0, 0)
            )
            selectedQuantity = it.getInt("selectedQuantity")
            totalCost = it.getDouble("totalCost")
        }

        val cartItems = mutableListOf<CartItem>()
        if (::selectedProduct.isInitialized) {
            cartItems.add(CartItem(selectedProduct.id, selectedQuantity, selectedProduct))
        }

        // Calculate the total value of each product and display it
        val totalCostTextView: TextView = findViewById(R.id.totalPriceTextView)
        val total = calculateTotal(AppCart.cart.products)
        totalCostTextView.text = String.format("Total: $%.2f", total)

        // Set up the button click listener
        val placeOrderButton: Button = findViewById(R.id.placeOrderButton)
        placeOrderButton.setOnClickListener {
            placeOrder()
        }

        setupCartRecyclerView(cartItems)
    }

    private fun calculateTotal(cartItems: List<CartItem>): Double {
        var total = 0.0
        cartItems.forEach { cartItem ->
            total += cartItem.product!!.price * cartItem.quantity
        }
        return total
    }

    private fun placeOrder() {
        val orderItems = AppCart.cart.products

        if (orderItems.isEmpty()) {
            Toast.makeText(this, "The cart is empty. Cannot place an order.", Toast.LENGTH_LONG).show()
            return
        }

        // Save the cart to SharedPreferences
        sharedPreferencesHelper.clearCart()

        // Clear the cart
        AppCart.clearCart()

        // Fetch carts and create a new Cart object for the placed order
        lifecycleScope.launch {
            val fetchedCarts = CartUtils.getCarts(sharedPreferencesHelper, fakeStoreApiClient)

            carts = fetchedCarts
            val placedOrders = sharedPreferencesHelper.loadPlacedOrders().toMutableList()
            val apiCarts = CartUtils.getCarts(sharedPreferencesHelper, fakeStoreApiClient)

            // Merge the carts fetched from the API with the placedOrders
            val allCarts = mutableListOf<Cart>().apply {
                addAll(apiCarts)
                addAll(placedOrders)
            }

            val placedOrder = Cart(
                id = allCarts.size + 1,
                userId = sharedPreferencesHelper.getUserId() ?: 0,
                date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                products = orderItems.toMutableList()
            )

            // Save the placed order to SharedPreferences
            placedOrders.add(placedOrder)
            sharedPreferencesHelper.savePlacedOrders(placedOrders)

            // Show order notification
            showOrderNotification()

            // Pass the order items to the OrderActivity
            val intent = Intent(this@CartActivity, OrderActivity::class.java)
            intent.putExtra("orderItems", orderItems as Serializable)
            startActivity(intent)
        }
    }

    private fun setupCartRecyclerView(cartItems: List<CartItem>) {
        val cartRecyclerView: RecyclerView = findViewById(R.id.cartRecyclerView)
        val totalCostTextView: TextView = findViewById(R.id.totalPriceTextView)

        val cartAdapter = CartAdapter(cartItems) { cartItem, newProductQuantity ->
            // Handle the quantity change here
            AppCart.updateProductQuantity(cartItem.productId, newProductQuantity)

            // Update the total value
            val updatedTotal = calculateTotal(AppCart.cart.products)
            totalCostTextView.text = String.format("Total: $%.2f", updatedTotal)
        }.also { adapter ->
            adapter.updateCartItems(AppCart.cart.products)
        }

        cartRecyclerView.layoutManager = LinearLayoutManager(this)
        cartRecyclerView.adapter = cartAdapter
    }
    @SuppressLint("MissingPermission")
    private fun showOrderNotification() {
        Log.d("CartActivity", "showOrderNotification called")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Shopping App")
            .setContentText("Thanks, your order has been placed!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sample Channel Category"
            val descriptionText = "Sample Channel Category Description"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onResume() {
        super.onResume()
        setupCartRecyclerView(cartItems = cart.items)
        val savedCart = sharedPreferencesHelper.getCart()
        if (savedCart != null) {
            AppCart.cart = savedCart
        }
    }

    override fun onPause() {
        super.onPause()
        sharedPreferencesHelper.saveCart(AppCart.cart)
    }
}
