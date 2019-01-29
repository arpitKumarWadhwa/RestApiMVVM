package com.codingwithmitch.foodrecipes.requests;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import com.codingwithmitch.foodrecipes.AppExecutors;
import com.codingwithmitch.foodrecipes.models.Recipe;
import com.codingwithmitch.foodrecipes.requests.responses.RecipeResponse;
import com.codingwithmitch.foodrecipes.requests.responses.RecipeSearchResponse;
import com.codingwithmitch.foodrecipes.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Response;

import static com.codingwithmitch.foodrecipes.util.Constants.NETWORK_TIMEOUT;


public class RecipeApiClient {

    private static final String TAG = "RecipeApiClient";

    private static RecipeApiClient instance;
    private MutableLiveData<List<Recipe>> mRecipes;
    private RetrieveRecipesRunnable mRetrieveRecipesRunnable;
    private MutableLiveData<Recipe> mRecipe;
    private RetrieveRecipeRunnable mRetrieveRecipeRunnable;
    private MutableLiveData<Boolean> mRecipeRequestTimedOut = new MutableLiveData<>();

    public static RecipeApiClient getInstance(){
        if(instance == null){
            instance = new RecipeApiClient();
        }
        return instance;
    }

    private RecipeApiClient() {
        mRecipes = new MutableLiveData<>();
        mRecipe = new MutableLiveData<>();
    }

    public LiveData<List<Recipe>> getRecipes(){
        return mRecipes;
    }

    public LiveData<Recipe> getRecipe(){
        return mRecipe;
    }

    public LiveData<Boolean> isRecipeRequestTimedOut(){
        return mRecipeRequestTimedOut;
    }

    public void searchRecipesApi(String query, int pageNumber){
        if(mRetrieveRecipesRunnable != null){
            mRetrieveRecipesRunnable = null;
        }
        mRetrieveRecipesRunnable = new RetrieveRecipesRunnable(query, pageNumber);
        final Future handler = AppExecutors.get().networkIO().submit(mRetrieveRecipesRunnable);

        // Set a timeout for the data refresh
        AppExecutors.get().networkIO().schedule(new Runnable() {
            @Override
            public void run() {
                // let the user know it timed out
                handler.cancel(true);
            }
        }, NETWORK_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public void searchRecipeById(String recipeId){
        mRecipeRequestTimedOut.setValue(false);
        if(mRetrieveRecipeRunnable != null){
            mRetrieveRecipeRunnable = null;
        }
        mRetrieveRecipeRunnable = new RetrieveRecipeRunnable(recipeId);
        final Future handler = AppExecutors.get().networkIO().submit(mRetrieveRecipeRunnable);

        // Set a timeout for the data refresh
        AppExecutors.get().networkIO().schedule(new Runnable() {
            @Override
            public void run() {
                // let the user know it timed out
                mRecipeRequestTimedOut.postValue(true);
                handler.cancel(true);
            }
        }, NETWORK_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private class RetrieveRecipesRunnable implements Runnable{

        private String query;
        private int pageNumber;
        private boolean cancelRequest;

        private RetrieveRecipesRunnable(String query, int pageNumber) {
            this.query = query;
            this.pageNumber = pageNumber;
            cancelRequest = false;
        }

        @Override
        public void run() {

            try {
                Response response = getRecipes(query, pageNumber).execute();
                if(cancelRequest){
                    return;
                }
                if(response.code() == 200){
                    List<Recipe> list = new ArrayList<>(((RecipeSearchResponse)response.body()).getRecipes());
                    if(pageNumber == 1){
                        mRecipes.postValue(list);
                    }
                    else{
                        List<Recipe> currentRecipes = mRecipes.getValue();
                        currentRecipes.addAll(list);
                        mRecipes.postValue(currentRecipes);
                    }
                }
                else{
                    String error = response.errorBody().string();
                    Log.e(TAG, "run: error: " + error);
                    mRecipes.postValue(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                mRecipes.postValue(null);
            }
        }

        private Call<RecipeSearchResponse> getRecipes(String query, int pageNumber){
            return ServiceGenerator.getRecipeApi().searchRecipe(
                    Constants.API_KEY,
                    query,
                    String.valueOf(pageNumber));
        }

        private void cancelRequest(){
            Log.d(TAG, "cancelRequest: canceling the retrieval query");
            cancelRequest = true;
        }
    }

    private class RetrieveRecipeRunnable implements Runnable{

        private String recipeId;
        private boolean cancelRequest;

        private RetrieveRecipeRunnable(String recipeId) {
            this.recipeId = recipeId;
            cancelRequest = false;
        }

        @Override
        public void run() {
            try {
                Response response = getRecipe(recipeId).execute();
                if(cancelRequest){
                    Log.d(TAG, "run: canceling the request.");
                    return;
                }
                Log.d(TAG, "run: response code: " + response.code());
                if(response.code() == 200){
                    Recipe recipe = ((RecipeResponse)response.body()).getRecipe();
                    mRecipe.postValue(recipe);
                }
                else{
                    String error = response.errorBody().string();
                    Log.e(TAG, "run: error: " + error );
                    mRecipe.postValue(null);
                }
            } catch (IOException e) {
                Log.e(TAG, "run: IOException: " + e.getMessage());
                mRecipe.postValue(null);
            }
        }

        private Call<RecipeResponse> getRecipe(String recipeId){
            return ServiceGenerator.getRecipeApi()
                    .getRecipe(
                            Constants.API_KEY,
                            recipeId
                    );
        }

        private void cancelRequest(){
            Log.d(TAG, "cancelRequest: canceling the refresh query.");
            cancelRequest = true;
        }
    }

    public void cancelRequest() {
        if(mRetrieveRecipesRunnable != null){
            mRetrieveRecipesRunnable.cancelRequest();
        }
        if(mRetrieveRecipeRunnable != null){
            mRetrieveRecipeRunnable.cancelRequest();
        }
    }
}






















