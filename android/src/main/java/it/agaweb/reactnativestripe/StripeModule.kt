package it.agaweb.reactnativestripe

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.*
import com.stripe.android.*
import com.stripe.android.model.*
import com.stripe.android.model.PaymentMethod.*
import com.stripe.android.view.AddPaymentMethodActivityStarter


class StripeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private lateinit var paymentPromise: Promise
  private lateinit var setupPromise: Promise
  private lateinit var stripe: Stripe
  private lateinit var publishableKey: String

  private val E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST"

  private val activityListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {

      // Handle the result of stripe.confirmPayment
      stripe.onPaymentResult(requestCode, data, object : ApiResultCallback<PaymentIntentResult> {
        override fun onSuccess(result: PaymentIntentResult) {
          val paymentIntent = result.intent
          val status = paymentIntent.status
          if (status == StripeIntent.Status.Succeeded) {
            paymentPromise.resolve(null)
          } else if (status == StripeIntent.Status.Canceled) {
            paymentPromise.reject("Stripe.Canceled", status.toString())
          } else {
            paymentPromise.reject("Stripe.OtherStatus", status.toString())
          }
        }

        override fun onError(e: Exception) {
          paymentPromise.reject("Stripe.Error", e.toString())
        }
      })

      // Handle the result of stripe.confirmSetupIntent
      stripe.onSetupResult(requestCode, data, object : ApiResultCallback<SetupIntentResult> {
        override fun onSuccess(result: SetupIntentResult) {
          val setupIntent = result.intent
          val status = setupIntent.status
          if (status == StripeIntent.Status.Succeeded) {
            setupPromise.resolve(null)
          } else if (status == StripeIntent.Status.Canceled) {
            setupPromise.reject("Stripe.Canceled", status.toString())
          } else {
            setupPromise.reject("Stripe.OtherStatus", status.toString())
          }
        }

        override fun onError(e: Exception) {
          setupPromise.reject("Stripe.Error", e.toString())
        }
      })
    }
  }

  init {
    reactApplicationContext.addActivityEventListener(activityListener)
  }

  override fun getName(): String {
    return "AgawebStripe"
  }

  @ReactMethod
  fun initModule(publishableKey: String) {
    PaymentConfiguration.init(
      reactApplicationContext,
      publishableKey
    )
    this.publishableKey = publishableKey
    this.stripe = Stripe(
      reactApplicationContext,
      PaymentConfiguration.getInstance(reactApplicationContext).publishableKey
    )
    Log.d("sometime","The key is $publishableKey");
  }

  @ReactMethod
  fun requestPaymentWithFpx(promise: Promise) {

    attachFPXPaymentResultActivityListener(promise);

    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
      return;
    }

//    // // already init
//    PaymentConfiguration.init(reactApplicationContext, publishableKey)


    // Store the promise to resolve/reject when picker returns data

    // Store the promise to resolve/reject when picker returns data
    paymentPromise = promise

    AddPaymentMethodActivityStarter(currentActivity!!)
      .startForResult(
        AddPaymentMethodActivityStarter
          .Args
          .Builder()
          .setPaymentMethodType(Type.Fpx)
          .build()
      )
  }


  private fun attachFPXPaymentResultActivityListener(promise: Promise) {
    val ael: ActivityEventListener = object : BaseActivityEventListener() {
      override fun onActivityResult(a: Activity, requestCode: Int, resultCode: Int, data: Intent) {
        val ael: ActivityEventListener = this
        reactApplicationContext.removeActivityEventListener(ael)
        val result: AddPaymentMethodActivityStarter.Result = AddPaymentMethodActivityStarter.Result.fromIntent(data)
        if (result is AddPaymentMethodActivityStarter.Result.Success) {
          val successResult: AddPaymentMethodActivityStarter.Result.Success = result

          // onPaymentMethodResult(successResult.getPaymentMethod());
          val paymentMethod: PaymentMethod = successResult.paymentMethod
          val fpxBankCode: String = paymentMethod.fpx!!.bank!!
          val resultMessage = """
                Created Payment Method

                Type: ${paymentMethod.type}
                Id: ${paymentMethod.id}
                Bank code: $fpxBankCode
                """.trimIndent()
          val fpxResult = Arguments.createMap()
          fpxResult.putString("type", paymentMethod.type.toString())
          fpxResult.putString("id", paymentMethod.id)
          fpxResult.putString("bankCode", fpxBankCode)
          promise.resolve(fpxResult)
        }
      }

//      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
//        onActivityResult(null, requestCode, resultCode, data)
//      }
    }
    reactApplicationContext.addActivityEventListener(ael)
  }


  @ReactMethod
  fun confirmPaymentWithCard(clientSecret: String, cardParams: ReadableMap, savePaymentMethod: Boolean, promise: Promise) {
    val card = PaymentMethodCreateParams.createCard(CardParams(
      cardParams.getString("number")!!,
      cardParams.getInt("expMonth"),
      cardParams.getInt("expYear"),
      cardParams.getString("cvc")
    ))

    val confirmParams = ConfirmPaymentIntentParams
      .createWithPaymentMethodCreateParams(card, clientSecret, null, savePaymentMethod);

    paymentPromise = promise;
    confirmPayment(confirmParams)
  }

  @ReactMethod
  fun confirmPaymentWithPaymentMethodId(clientSecret: String, paymentMethodId: String, promise: Promise) {
    val confirmParams = ConfirmPaymentIntentParams
      .createWithPaymentMethodId(paymentMethodId, clientSecret, "stripejs://use_stripe_sdk/return_url")

    paymentPromise = promise
    confirmPayment(confirmParams)
  }

  @ReactMethod
  fun confirmCardSetup(clientSecret: String, cardParams: ReadableMap, promise: Promise) {
    val card = PaymentMethodCreateParams.createCard(CardParams(
      cardParams.getString("number")!!,
      cardParams.getInt("expMonth"),
      cardParams.getInt("expYear"),
      cardParams.getString("cvc")
    ))

    val confirmParams = ConfirmSetupIntentParams
      .create(card, clientSecret);

    setupPromise = promise;
    confirmSetupIntent(confirmParams)
  }

  private fun confirmPayment(confirmParams: ConfirmPaymentIntentParams) {
//    stripe = Stripe(
//      reactApplicationContext,
//      PaymentConfiguration.getInstance(reactApplicationContext).publishableKey
//    )
    stripe.confirmPayment(currentActivity!!, confirmParams)
  }

  private fun confirmSetupIntent(confirmParams: ConfirmSetupIntentParams) {
    stripe = Stripe(
      reactApplicationContext,
      PaymentConfiguration.getInstance(reactApplicationContext).publishableKey
    )
    stripe.confirmSetupIntent(currentActivity!!, confirmParams)
  }
}
